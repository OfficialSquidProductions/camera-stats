import XCTest
@testable import CameraStatsCore

final class MalformedArchiveTests:XCTestCase {
    func testArchiveMutationsAndPrefixesNeverCrash() throws {
        let data=try Exchange.export(.demo(),device:"Fuzz fixture")
        for length in [0,1,3,4,10,22,30,100,data.count-1] { XCTAssertThrowsError(try Exchange.read(Data(data.prefix(length)))) }
        for offset in stride(from:0,to:data.count,by:max(1,data.count/200)) {var changed=data; changed[offset] ^= 0xff; _=try? Exchange.read(changed)}
        var prefixed=Data([0xaa]); prefixed+=data
        XCTAssertEqual(try Exchange.read(prefixed.dropFirst()).report.entries.count,3)
    }
    func testDuplicateFilesCompressionFlagsAndTraversalAreRejected() throws {
        for name in ["/report.json","../report.json","a/../report.json","C:\\report.json","report.json\0bad"] { XCTAssertThrowsError(try StoredZIP.read(StoredZIP.write([(name,Data("x".utf8))]))) }
        XCTAssertThrowsError(try StoredZIP.read(StoredZIP.write([("report.json",Data()),("report.json",Data())])))
        var compressed=try Exchange.export(.demo(),device:"Test"); compressed[8]=8; XCTAssertThrowsError(try Exchange.read(compressed))
        var encrypted=try Exchange.export(.demo(),device:"Test"); encrypted[6]=1; XCTAssertThrowsError(try Exchange.read(encrypted))
        var huge=try Exchange.export(.demo(),device:"Test"); for i in 18..<22 {huge[i]=0xff}; XCTAssertThrowsError(try Exchange.read(huge))
    }
    func testEntryIdentityCollisionIsRejected() throws {
        var report=Report.demo(); report.entries[1].id=report.entries[0].id
        XCTAssertThrowsError(try Exchange.read(Exchange.export(report,device:"Test")))
    }
}
