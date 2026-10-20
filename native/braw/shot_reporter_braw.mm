// Original Shot Reporter adapter. Vendor declarations and dispatch implementation
// are supplied by a separately licensed, local SDK; they are not in this repo.
#include "BlackmagicRawAPI.h"
#import <Foundation/Foundation.h>
#include <atomic>
#include <chrono>
#include <cmath>
#include <condition_variable>
#include <cstring>
#include <fcntl.h>
#include <filesystem>
#include <iomanip>
#include <iostream>
#include <map>
#include <mutex>
#include <numeric>
#include <sstream>
#include <stdexcept>
#include <string>
#include <sys/stat.h>
#include <unistd.h>
#include <vector>

namespace fs = std::filesystem;
struct Failure : std::runtime_error {
    using std::runtime_error::runtime_error;
};
static void trace(const char *stage) {
    if (getenv("SHOT_REPORTER_BRAW_TRACE"))
        std::cerr << stage << "\n";
}
static void checked(HRESULT result, const char *operation) {
    if (FAILED(result)) {
        std::ostringstream message;
        message << operation << " failed (SDK result " << std::hex << uint32_t(result) << ")";
        throw Failure(message.str());
    }
}
template <class T> struct Owned {
    T *value = nullptr;
    ~Owned() {
        if (value)
            value->Release();
    }
    T **out() { return &value; }
    T *operator->() const { return value; }
    Owned() = default;
    Owned(const Owned &) = delete;
    Owned &operator=(const Owned &) = delete;
};
static std::string utf8(CFStringRef value) {
    if (!value)
        return {};
    const CFIndex count = CFStringGetLength(value);
    if (count > 16384)
        throw Failure("SDK text field exceeds limit");
    const CFIndex size = CFStringGetMaximumSizeForEncoding(count, kCFStringEncodingUTF8) + 1;
    std::vector<char> bytes(size);
    if (!CFStringGetCString(value, bytes.data(), size, kCFStringEncodingUTF8))
        throw Failure("Invalid SDK text field");
    return bytes.data();
}
static std::string takeString(CFStringRef value) {
    std::string result = utf8(value);
    if (value)
        CFRelease(value);
    return result;
}
static NSString *text(const std::string &value) {
    return [[NSString alloc] initWithBytes:value.data() length:value.size() encoding:NSUTF8StringEncoding];
}
static void emit(NSDictionary *object) {
    NSError *error = nil;
    NSData *data = [NSJSONSerialization dataWithJSONObject:object
                                                   options:NSJSONWritingSortedKeys
                                                     error:&error];
    if (!data || data.length > 1024 * 1024)
        throw Failure("Could not encode bounded JSON response");
    std::cout.write(static_cast<const char *>(data.bytes), data.length);
    std::cout << '\n';
}
static void sourcePath(const char *argument) {
    fs::path path(argument);
    struct stat info{};
    if (!path.is_absolute() || lstat(argument, &info) != 0 || !S_ISREG(info.st_mode) ||
        fs::canonical(path) != path.lexically_normal())
        throw Failure("Input must be an existing absolute regular file without symbolic links");
    if (path.extension() != ".braw" && path.extension() != ".BRAW")
        throw Failure("Input must be a BRAW logical clip");
}
static void outputPath(const char *argument) {
    fs::path path(argument);
    if (!path.is_absolute() || path.filename().empty() ||
        fs::canonical(path.parent_path()) != path.parent_path().lexically_normal())
        throw Failure("Output must be an absolute path in an existing directory without symbolic links");
    struct stat info{};
    if (lstat(argument, &info) == 0 || errno != ENOENT)
        throw Failure("Output already exists or cannot be checked");
}
static uint64_t integer(const char *value) {
    if (!*value)
        throw Failure("Frame index must be an unsigned decimal integer");
    for (const char *p = value; *p; ++p)
        if (*p < '0' || *p > '9')
            throw Failure("Frame index must be an unsigned decimal integer");
    size_t end = 0;
    uint64_t result = std::stoull(value, &end);
    if (end != std::strlen(value))
        throw Failure("Invalid frame index");
    return result;
}
static std::pair<uint64_t, uint64_t> rateFraction(float rate) {
    if (!std::isfinite(rate) || rate < 0.0001 || rate > 10000)
        throw Failure("Invalid SDK frame rate");
    int power = 0;
    float fraction = std::frexp(rate, &power);
    uint64_t numerator = std::llround(std::ldexp(double(fraction), 24)), denominator = 1;
    if (power < 24)
        denominator = uint64_t(1) << (24 - power);
    else
        numerator <<= (power - 24);
    auto divisor = std::gcd(numerator, denominator);
    return {numerator / divisor, denominator / divisor};
}
static std::string ratio(uint64_t numerator, uint64_t denominator) {
    return std::to_string(numerator) + "/" + std::to_string(denominator);
}
static std::string number(double value) {
    std::ostringstream result;
    result << std::setprecision(12) << value;
    return result.str();
}
static void writeAll(int descriptor, const void *data, size_t count) {
    const char *bytes = static_cast<const char *>(data);
    while (count) {
        ssize_t result = write(descriptor, bytes, count);
        if (result < 0 && errno == EINTR)
            continue;
        if (result <= 0)
            throw Failure("Frame output write failed");
        bytes += result;
        count -= result;
    }
}

class Callback final : public IBlackmagicRawCallback {
    std::atomic<ULONG> references{1};

  public:
    std::mutex mutex;
    std::condition_variable condition;
    bool done = false;
    std::string error;
    uint64_t expected = 0, actual = 0;
    uint32_t width = 0, height = 0;
    std::vector<uint8_t> rgb;
    std::string timecode;
    void finishError(const char *message) {
        trace(message);
        std::lock_guard<std::mutex> guard(mutex);
        if (error.empty())
            error = message;
        done = true;
        condition.notify_all();
    }
    HRESULT QueryInterface(REFIID id, LPVOID *result) override {
        if (!result)
            return E_POINTER;
        *result = nullptr;
        const auto unknown = CFUUIDGetUUIDBytes(IUnknownUUID);
        if (std::memcmp(&id, &unknown, sizeof(id)) == 0 ||
            std::memcmp(&id, &IID_IBlackmagicRawCallback, sizeof(id)) == 0) {
            *result = static_cast<IBlackmagicRawCallback *>(this);
            AddRef();
            return S_OK;
        }
        return E_NOINTERFACE;
    }
    ULONG AddRef() override { return ++references; }
    ULONG Release() override {
        ULONG count = --references;
        if (!count)
            delete this;
        return count;
    }
    void ReadComplete(IBlackmagicRawJob *, HRESULT result, IBlackmagicRawFrame *frame) override {
        try {
            trace("read callback");
            checked(result, "Read frame");
            if (!frame)
                throw Failure("SDK returned no frame");
            checked(frame->GetFrameIndex(&actual), "Read native frame index");
            if (actual != expected)
                throw Failure("SDK frame index does not match requested index");
            trace("index checked");
            CFStringRef tc = nullptr;
            if (SUCCEEDED(frame->GetTimecode(&tc)))
                timecode = takeString(tc);
            trace("timecode checked");
            checked(frame->SetResolutionScale(blackmagicRawResolutionScaleFull), "Select full resolution");
            checked(frame->SetResourceFormat(blackmagicRawResourceFormatRGBAU8), "Select RGBA8");
            Owned<IBlackmagicRawJob> job;
            checked(frame->CreateJobDecodeAndProcessFrame(nullptr, nullptr, job.out()), "Create decode job");
            trace("decode created");
            checked(job->Submit(), "Submit decode job");
            trace("decode submitted");
        } catch (const std::exception &issue) {
            finishError(issue.what());
        }
    }
    void ProcessComplete(IBlackmagicRawJob *, HRESULT result, IBlackmagicRawProcessedImage *image) override {
        try {
            trace("process callback");
            checked(result, "Process frame");
            if (!image)
                throw Failure("SDK returned no image");
            BlackmagicRawResourceType type{};
            BlackmagicRawResourceFormat format{};
            uint32_t size = 0;
            void *resource = nullptr;
            checked(image->GetWidth(&width), "Image width");
            trace("image width checked");
            checked(image->GetHeight(&height), "Image height");
            trace("image height checked");
            checked(image->GetResourceType(&type), "Image resource type");
            trace("resource type checked");
            checked(image->GetResourceFormat(&format), "Image format");
            trace("format checked");
            checked(image->GetResourceSizeBytes(&size), "Image resource size");
            trace("resource size checked");
            checked(image->GetResource(&resource), "Image resource");
            trace("resource checked");
            uint64_t pixels = uint64_t(width) * height;
            if (!width || !height || width > 32768 || height > 32768 || pixels > 67108864 ||
                type != blackmagicRawResourceTypeBufferCPU || format != blackmagicRawResourceFormatRGBAU8 ||
                size != pixels * 4 || !resource)
                throw Failure("Unsupported image resource or dimensions");
            trace("resource validated");
            rgb.resize(pixels * 3);
            auto *rgba = static_cast<uint8_t *>(resource);
            for (uint64_t pixel = 0; pixel < pixels; ++pixel) {
                rgb[pixel * 3] = rgba[pixel * 4];
                rgb[pixel * 3 + 1] = rgba[pixel * 4 + 1];
                rgb[pixel * 3 + 2] = rgba[pixel * 4 + 2];
            }
            trace("pixels copied");
            std::lock_guard<std::mutex> guard(mutex);
            done = true;
            condition.notify_all();
            trace("done signalled");
        } catch (const std::exception &issue) {
            finishError(issue.what());
        }
    }
    void ReadAudioComplete(IBlackmagicRawJob *, HRESULT, IBlackmagicRawAudioBuffer *) override {}
    void DecodeComplete(IBlackmagicRawJob *, HRESULT) override {}
    void TrimProgress(IBlackmagicRawJob *, float) override {}
    void TrimComplete(IBlackmagicRawJob *, HRESULT) override {}
    void PreparePipelineComplete(void *, HRESULT) override {}
    void SidecarMetadataParseWarning(IBlackmagicRawClip *, CFStringRef, uint32_t, CFStringRef) override {
        finishError("SDK warned about sidecar metadata; refusing an ambiguous decode");
    }
    void SidecarMetadataParseError(IBlackmagicRawClip *, CFStringRef, uint32_t, CFStringRef) override {
        finishError("SDK could not parse attached sidecar; refusing an ambiguous decode");
    }
};
struct Session {
    Owned<IBlackmagicRawFactory> factory;
    Owned<IBlackmagicRaw> codec;
    Owned<IBlackmagicRawConfiguration> config;
    Owned<IBlackmagicRawClip> clip;
    Owned<Callback> callback;
    std::string frameworkVersion, frameworkBuild;
    Session() {
        const char *library = getenv("SHOT_REPORTER_BRAW_LIBRARY");
#ifdef SHOT_REPORTER_BRAW_LIBRARY_DEFAULT
        if (!library)
            library = SHOT_REPORTER_BRAW_LIBRARY_DEFAULT;
#endif
        if (!library || !fs::path(library).is_absolute())
            throw Failure("A local authorized SDK library directory is required");
        fs::path bundlePath = fs::path(library) / "BlackmagicRawAPI.framework";
        CFURLRef bundleURL = CFURLCreateFromFileSystemRepresentation(
            nullptr, reinterpret_cast<const UInt8 *>(bundlePath.c_str()), std::strlen(bundlePath.c_str()),
            true);
        if (bundleURL) {
            CFBundleRef bundle = CFBundleCreate(nullptr, bundleURL);
            if (bundle) {
                CFTypeRef version =
                    CFBundleGetValueForInfoDictionaryKey(bundle, CFSTR("CFBundleShortVersionString"));
                if (version && CFGetTypeID(version) == CFStringGetTypeID())
                    frameworkVersion = utf8(static_cast<CFStringRef>(version));
                CFTypeRef build = CFBundleGetValueForInfoDictionaryKey(bundle, CFSTR("CFBundleVersion"));
                if (build && CFGetTypeID(build) == CFStringGetTypeID())
                    frameworkBuild = utf8(static_cast<CFStringRef>(build));
                CFRelease(bundle);
            }
            CFRelease(bundleURL);
        }
#ifdef SHOT_REPORTER_BRAW_EXPECTED_VERSION
        if (frameworkVersion != SHOT_REPORTER_BRAW_EXPECTED_VERSION)
            throw Failure("Blackmagic RAW runtime version does not match the SDK used to build this helper; install matching versions before decoding");
#else
        throw Failure("Rebuild this helper with the SDK runtime version check before decoding");
#endif
        CFStringRef path = CFStringCreateWithCString(nullptr, library, kCFStringEncodingUTF8);
        factory.value = CreateBlackmagicRawFactoryInstanceFromPath(path);
        CFRelease(path);
        if (!factory.value)
            throw Failure("Could not load authorized Blackmagic RAW SDK runtime");
        checked(factory->CreateCodec(codec.out()), "Create codec");
        checked(
            codec->QueryInterface(IID_IBlackmagicRawConfiguration, reinterpret_cast<void **>(config.out())),
            "Get codec configuration");
        checked(config->SetPipeline(blackmagicRawPipelineCPU, nullptr, nullptr), "Select CPU pipeline");
        checked(config->SetCPUThreads(4), "Bound decoder threads");
        callback.value = new Callback();
        checked(codec->SetCallback(callback.value), "Set decoder callback");
    }
    ~Session() {
        trace("session teardown");
        if (codec.value) {
            codec->FlushJobs();
            trace("teardown flushed");
            codec->SetCallback(nullptr);
            trace("callback removed");
        }
    }
    std::string version() {
        CFStringRef value = nullptr;
        checked(config->GetVersion(&value), "Get SDK version");
        return takeString(value);
    }
    void open(const char *file) {
        sourcePath(file);
        CFStringRef path = CFStringCreateWithCString(nullptr, file, kCFStringEncodingUTF8);
        HRESULT result = codec->OpenClip(path, clip.out());
        CFRelease(path);
        checked(result, "Open BRAW logical clip");
        if (!callback->error.empty())
            throw Failure(callback->error);
        uint32_t cards = 0;
        checked(clip->GetMulticardFileCount(&cards), "Get multicard count");
        for (uint32_t index = 0; index < cards; ++index) {
            bool present = false;
            checked(clip->IsMulticardFilePresent(index, &present), "Check multicard member");
            if (!present)
                throw Failure("BRAW logical clip is missing a multicard member");
        }
    }
    void frame(uint64_t index) {
        uint64_t count = 0;
        checked(clip->GetFrameCount(&count), "Get frame count");
        if (index >= count)
            throw Failure("Frame index is outside the BRAW logical clip");
        callback->expected = index;
        Owned<IBlackmagicRawJob> job;
        checked(clip->CreateJobReadFrame(index, job.out()), "Create read job");
        checked(job->Submit(), "Submit read job");
        trace("read submitted");
        std::unique_lock<std::mutex> lock(callback->mutex);
        if (!callback->condition.wait_for(lock, std::chrono::seconds(45), [&] { return callback->done; })) {
            lock.unlock();
            job->Abort();
            throw Failure("Frame decode timed out");
        }
        trace("wait finished");
        if (!callback->error.empty())
            throw Failure(callback->error);
        lock.unlock();
        trace("before flush");
        job.value->Release();
        job.value = nullptr;
        checked(codec->FlushJobs(), "Flush frame job");
        trace("after flush");
    }
};
static NSDictionary *probe(Session &session) {
    uint32_t width = 0, height = 0, cards = 0;
    uint64_t count = 0;
    float fps = 0;
    bool sidecar = false;
    checked(session.clip->GetWidth(&width), "Get width");
    checked(session.clip->GetHeight(&height), "Get height");
    checked(session.clip->GetFrameCount(&count), "Get frame count");
    checked(session.clip->GetFrameRate(&fps), "Get frame rate");
    checked(session.clip->GetMulticardFileCount(&cards), "Get multicard count");
    checked(session.clip->GetSidecarFileAttached(&sidecar), "Get sidecar state");
    if (!width || !height || !count)
        throw Failure("BRAW clip has no valid video frames");
    auto rate = rateFraction(fps);
    NSMutableDictionary *tags = [NSMutableDictionary dictionaryWithDictionary:@{
        @"sdkVersion" : text(session.frameworkVersion),
        @"sdkApiVersion" : text(session.version()),
        @"sdkBuild" : text(session.frameworkBuild),
        @"decoder" : @"Blackmagic RAW SDK CPU",
        @"timestampMeaning" : @"clip-relative frame index; not source packet PTS",
        @"rateSource" :
            @"exact rational representation of SDK GetFrameRate float32; source-native rational unavailable",
        @"rendering" : sidecar ? @"SDK defaults with attached sidecar" : @"SDK defaults without sidecar",
        @"sidecarAttached" : sidecar ? @"true" : @"false",
        @"multicardCount" : text(std::to_string(cards)),
        @"sdkFrameRateFloat" : text(number(fps))
    }];
    CFStringRef camera = nullptr;
    if (SUCCEEDED(session.clip->GetCameraType(&camera)) && camera)
        tags[@"cameraType"] = text(takeString(camera));
    CFStringRef tc = nullptr;
    if (SUCCEEDED(session.clip->GetTimecodeForFrame(0, &tc)) && tc)
        tags[@"timecode"] = text(takeString(tc));
    tc = nullptr;
    if (SUCCEEDED(session.clip->GetTimecodeForFrame(count - 1, &tc)) && tc)
        tags[@"lastFrameTimecode"] = text(takeString(tc));
    return @{
        @"codec" : @"braw",
        @"container" : @"braw",
        @"width" : @(width),
        @"height" : @(height),
        @"rate" : text(ratio(rate.first, rate.second)),
        @"timeBase" : text(ratio(rate.second, rate.first)),
        @"frames" : @(count),
        @"duration" : text(number(double(count) / fps)),
        @"tags" : tags,
        @"readable" : @YES
    };
}
int main(int argc, const char *argv[]) {
    @autoreleasepool {
        try {
            if (argc < 2)
                throw Failure(
                    "Use version, probe absolute.braw, or frame absolute.braw index new-output.ppm");
            std::string command = argv[1];
            if ((command == "version" && argc != 2) || (command == "probe" && argc != 3) ||
                (command == "frame" && argc != 5) ||
                (command != "version" && command != "probe" && command != "frame"))
                throw Failure("Invalid adapter command");
            if (command == "frame")
                outputPath(argv[4]);
            trace("construct session");
            Session session;
            trace("session constructed");
            if (command == "version") {
                emit(@{
                    @"adapter" : @"shot-reporter-braw",
                    @"sdkVersion" : text(session.frameworkVersion),
                    @"sdkApiVersion" : text(session.version()),
                    @"sdkBuild" : text(session.frameworkBuild)
                });
                return 0;
            }
            session.open(argv[2]);
            trace("clip opened");
            if (command == "probe") {
                emit(probe(session));
                return 0;
            }
            uint64_t index = integer(argv[3]);
            session.frame(index);
            trace("frame ready");
            auto &frame = *session.callback.value;
            int descriptor = open(argv[4], O_WRONLY | O_CREAT | O_EXCL | O_NOFOLLOW, 0600);
            if (descriptor < 0)
                throw Failure("Frame output could not be created without replacing a file");
            try {
                std::string header =
                    "P6\n" + std::to_string(frame.width) + " " + std::to_string(frame.height) + "\n255\n";
                writeAll(descriptor, header.data(), header.size());
                writeAll(descriptor, frame.rgb.data(), frame.rgb.size());
                if (fsync(descriptor) != 0)
                    throw Failure("Frame output could not be flushed");
                close(descriptor);
            } catch (...) {
                close(descriptor);
                throw;
            }
            emit(@{
                @"index" : @(frame.actual),
                @"pts" : @(frame.actual),
                @"width" : @(frame.width),
                @"height" : @(frame.height),
                @"timecode" : text(frame.timecode),
                @"timestampMeaning" : @"clip-relative frame index; not source packet PTS"
            });
            return 0;
        } catch (const std::exception &error) {
            std::cerr << "BRAW adapter: " << error.what() << '\n';
            return 2;
        }
    }
}
