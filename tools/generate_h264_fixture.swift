import Foundation
import AVFoundation
import AppKit

// Synthetic, independently numbered frames. No user footage or external encoder SDK.
guard CommandLine.arguments.count==2 else {fatalError("Choose a fresh output .mp4 or .mov path")}
let url=URL(fileURLWithPath:CommandLine.arguments[1])
guard !FileManager.default.fileExists(atPath:url.path),["mp4","mov"].contains(url.pathExtension.lowercased()) else {fatalError("A new MP4/MOV fixture path is required; no files overwritten")}
let writer=try AVAssetWriter(outputURL:url,fileType:url.pathExtension.lowercased()=="mov" ? .mov:.mp4)
let input=AVAssetWriterInput(mediaType:.video,outputSettings:[AVVideoCodecKey:AVVideoCodecType.h264,AVVideoWidthKey:640,AVVideoHeightKey:360,AVVideoCompressionPropertiesKey:[AVVideoAverageBitRateKey:600000,AVVideoProfileLevelKey:AVVideoProfileLevelH264HighAutoLevel,AVVideoAllowFrameReorderingKey:true]])
let adaptor=AVAssetWriterInputPixelBufferAdaptor(assetWriterInput:input,sourcePixelBufferAttributes:[kCVPixelBufferPixelFormatTypeKey as String:kCVPixelFormatType_32ARGB,kCVPixelBufferWidthKey as String:640,kCVPixelBufferHeightKey as String:360,kCVPixelBufferCGImageCompatibilityKey as String:true,kCVPixelBufferCGBitmapContextCompatibilityKey as String:true])
writer.add(input);guard writer.startWriting() else {throw writer.error!};writer.startSession(atSourceTime:.zero)
for n in 0..<24 {
    let deadline=Date().addingTimeInterval(10)
    while !input.isReadyForMoreMediaData {guard Date()<deadline,writer.status == .writing else {throw writer.error ?? NSError(domain:"Fixture",code:1)};Thread.sleep(forTimeInterval:0.005)}
    var pixel:CVPixelBuffer?;guard CVPixelBufferPoolCreatePixelBuffer(nil,adaptor.pixelBufferPool!,&pixel)==kCVReturnSuccess,let pixel else {fatalError("Pixel buffer unavailable")}
    CVPixelBufferLockBaseAddress(pixel,[])
    let context=CGContext(data:CVPixelBufferGetBaseAddress(pixel),width:640,height:360,bitsPerComponent:8,bytesPerRow:CVPixelBufferGetBytesPerRow(pixel),space:CGColorSpaceCreateDeviceRGB(),bitmapInfo:CGImageAlphaInfo.noneSkipFirst.rawValue)!
    context.setFillColor(n<12 ? NSColor(red:0.90,green:0.08,blue:0.16,alpha:1).cgColor:NSColor(red:0.08,green:0.16,blue:0.90,alpha:1).cgColor);context.fill(CGRect(x:0,y:0,width:640,height:360))
    context.setFillColor(NSColor(white:0.96,alpha:1).cgColor);context.fill(CGRect(x:80,y:60,width:480,height:240))
    NSGraphicsContext.saveGraphicsState();NSGraphicsContext.current=NSGraphicsContext(cgContext:context,flipped:false)
    let attributes:[NSAttributedString.Key:Any]=[.font:NSFont.boldSystemFont(ofSize:24),.foregroundColor:NSColor.black]
    ("THE LAST LIGHT · SYNTHETIC" as NSString).draw(at:NSPoint(x:100,y:245),withAttributes:attributes)
    ("SCENE 3A · TAKE 2 · CAM A" as NSString).draw(at:NSPoint(x:100,y:180),withAttributes:attributes)
    (String(format:"SOURCE FRAME %02d",n) as NSString).draw(at:NSPoint(x:150,y:110),withAttributes:attributes)
    NSGraphicsContext.restoreGraphicsState();CVPixelBufferUnlockBaseAddress(pixel,[])
    guard adaptor.append(pixel,withPresentationTime:CMTime(value:Int64(n),timescale:12)) else {throw writer.error!}
}
input.markAsFinished();let done=DispatchSemaphore(value:0);writer.finishWriting {done.signal()}
guard done.wait(timeout:.now()+30) == .success,writer.status == .completed else {throw writer.error ?? NSError(domain:"Fixture",code:2)}
print("Created synthetic H.264 fixture: \(url.lastPathComponent), 24 frames, 12 fps. No camera compatibility inferred.")
