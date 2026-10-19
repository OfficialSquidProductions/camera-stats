#if os(macOS)
import Foundation

struct DesktopMediaRoot:Codable,Identifiable {let id:String;let path:String;let label:String;let camera:String;let roll:String;let load:String}
struct DesktopPhysicalFile:Codable,Identifiable {let id:String;let rootId:String;let relative:String;let size:Int64;let sha256:String;let modified:Int64}
struct DesktopProbe:Codable {let codec:String;let container:String;let width:Int;let height:Int;let rate:String;let timeBase:String;let duration:String;let frames:Int64?;let tags:[String:String];let readable:Bool;let issue:String}
struct DesktopClip:Codable,Identifiable {let id:String;let fileId:String;let rootId:String;let originalName:String;let displayName:String;let metadata:DesktopProbe;let companions:[String];var memberFileIds:[String]?=nil;var packageRoot:String?=nil;var packageKind:String?=nil;var packageFingerprint:String?=nil;var packageIssue:String?=nil}
struct DesktopFrame:Codable,Identifiable {let id:String;let clipId:String;let fileSHA256:String;let index:Int64;let pts:String;let timeBase:String;let png:String;let pngSHA256:String;let decoder:String;var packageFingerprint:String?=nil}
struct DesktopPackageApproval:Codable {let clipId:String;let fingerprint:String;let confirmedAt:String;var completeSourceConfirmed:Bool?=nil}
struct DesktopSlate:Codable,Identifiable {var id:String {clipId};let clipId:String;let scene:String;let take:String;let frame:DesktopFrame;let confirmedAt:String;let source:String}
struct DesktopAssociation:Codable,Identifiable {let id:String;let clipId:String;let reportId:String;let entryId:String;let reportRevision:Int64;let reportFingerprint:String;let reasons:[String];let confirmedAt:String;let inFrame:Int64?;let outFrame:Int64?}
struct DesktopSuggestion:Codable,Identifiable {var id:String {entryId};let entryId:String;let label:String;let score:Int;let reasons:[String];let ambiguous:Bool;let conflicts:[String]}
struct DesktopPreset:Codable,Identifiable {var id:String=UUID().uuidString;var version:Int=1;var name:String;var template:String
    static let movie="{project}_SC{scene}_TK{take:03}_CAM-{camera}_{original}"
    static let television="{project}_S{season:02}E{episode:02}_SC{scene}_TK{take:03}_CAM-{camera}_{original}"
}
struct DesktopCopyItem:Codable,Identifiable {var id:String {fileId};let clipId:String;let fileId:String;let rootId:String;let sourceRelative:String;let sourceSHA256:String;let size:Int64;let targetName:String;let scene:String;let take:String;let frame:DesktopFrame;let association:DesktopAssociation;let resolved:[String:String];let state:String;let stages:[String];let publishedKey:String?;let undoRelative:String?}
struct DesktopCopyJob:Codable,Identifiable {let id:String;let projectId:String;let reportRevision:Int64;let reportFingerprint:String;let preset:DesktopPreset;let destination:String;let createdAt:String;let items:[DesktopCopyItem];let state:String;let error:String?}
struct DesktopMediaAudit:Codable,Identifiable {let id:String;let subject:String;let action:String;let detail:String;let time:String}
struct DesktopMediaSnapshot:Codable {
    var roots:[DesktopMediaRoot]=[];var files:[DesktopPhysicalFile]=[];var clips:[DesktopClip]=[];var slates:[DesktopSlate]=[];var associations:[DesktopAssociation]=[];var jobs:[DesktopCopyJob]=[];var presets:[DesktopPreset]=[];var audit:[DesktopMediaAudit]=[]
    var packageApprovals:[DesktopPackageApproval]?=nil
}
struct DesktopWorkerResponse<T:Decodable>:Decodable {let ok:Bool;let result:T?;let error:String?}
#endif
