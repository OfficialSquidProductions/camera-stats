import AppKit
import Foundation
let root = URL(fileURLWithPath:CommandLine.arguments[1])
let source = NSImage(contentsOf:root.appendingPathComponent("assets/brand/SP_GREEN.png"))!
let folder = root.appendingPathComponent("apple/Assets.xcassets/AppIcon.appiconset")
try FileManager.default.createDirectory(at:folder,withIntermediateDirectories:true)
var items:[[String:String]] = []
func make(_ pixels:Int,_ name:String,mac:Bool = true) throws {
    let rep = NSBitmapImageRep(bitmapDataPlanes:nil,pixelsWide:pixels,pixelsHigh:pixels,bitsPerSample:8,samplesPerPixel:4,hasAlpha:true,isPlanar:false,colorSpaceName:.deviceRGB,bytesPerRow:0,bitsPerPixel:0)!
    NSGraphicsContext.saveGraphicsState(); NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep:rep)
    let cg = NSGraphicsContext.current!.cgContext
    cg.clear(CGRect(x:0,y:0,width:pixels,height:pixels)); cg.scaleBy(x:CGFloat(pixels)/1024,y:CGFloat(pixels)/1024)
    NSColor.white.setFill()
    if mac {
        // Exact shared suite geometry, verified against installed Scenic/Production Desk icons.
        NSBezierPath(roundedRect:NSRect(x:40,y:40,width:944,height:944),xRadius:205,yRadius:205).fill()
    } else { NSRect(x:0,y:0,width:1024,height:1024).fill() }
    source.draw(in:NSRect(x:85,y:85,width:854,height:854),from:.zero,operation:.sourceOver,fraction:1)
    NSGraphicsContext.restoreGraphicsState()
    var output = rep
    if !mac {
        let opaque = CGContext(data:nil,width:pixels,height:pixels,bitsPerComponent:8,bytesPerRow:0,space:CGColorSpaceCreateDeviceRGB(),bitmapInfo:CGImageAlphaInfo.noneSkipLast.rawValue)!
        opaque.draw(rep.cgImage!,in:CGRect(x:0,y:0,width:pixels,height:pixels)); output = NSBitmapImageRep(cgImage:opaque.makeImage()!)
        precondition(!output.hasAlpha,"iOS icon must not carry an alpha channel")
    }
    try output.representation(using:.png,properties:[:])!.write(to:folder.appendingPathComponent(name))
}
for size in [16,32,128,256,512] { for scale in [1,2] { let name = "mac-\(size)-\(scale).png"; try make(size*scale,name); items.append(["idiom":"mac","size":"\(size)x\(size)","scale":"\(scale)x","filename":name]) } }
try make(1024,"ios-1024.png",mac:false); items.append(["idiom":"universal","platform":"ios","size":"1024x1024","filename":"ios-1024.png"])
let json:[String:Any] = ["images":items,"info":["author":"xcode","version":1]]
try JSONSerialization.data(withJSONObject:json,options:.prettyPrinted).write(to:folder.appendingPathComponent("Contents.json"))

// Small transparent web mark; the original 2000px brand source stays unchanged.
let web = NSBitmapImageRep(bitmapDataPlanes:nil,pixelsWide:256,pixelsHigh:256,bitsPerSample:8,samplesPerPixel:4,hasAlpha:true,isPlanar:false,colorSpaceName:.deviceRGB,bytesPerRow:0,bitsPerPixel:0)!
NSGraphicsContext.saveGraphicsState(); NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep:web)
NSGraphicsContext.current!.cgContext.clear(CGRect(x:0,y:0,width:256,height:256)); source.draw(in:NSRect(x:0,y:0,width:256,height:256),from:.zero,operation:.sourceOver,fraction:1); NSGraphicsContext.restoreGraphicsState()
let webAssets = root.appendingPathComponent("website/assets")
try FileManager.default.createDirectory(at:webAssets,withIntermediateDirectories:true)
try web.representation(using:.png,properties:[:])!.write(to:webAssets.appendingPathComponent("SP_GREEN.png"))
