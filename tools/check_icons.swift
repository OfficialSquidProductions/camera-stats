import AppKit
import Foundation
guard CommandLine.arguments.count == 3 else {
    fputs("Usage: swift tools/check_icons.swift <project-root> <suite-reference-1024.png>\n",stderr); exit(2)
}
let root = URL(fileURLWithPath:CommandLine.arguments[1])
let ours = NSBitmapImageRep(data:try Data(contentsOf:root.appendingPathComponent("apple/Assets.xcassets/AppIcon.appiconset/mac-512-2.png")))!
let ios = NSBitmapImageRep(data:try Data(contentsOf:root.appendingPathComponent("apple/Assets.xcassets/AppIcon.appiconset/ios-1024.png")))!
let reference = NSBitmapImageRep(data:try Data(contentsOf:URL(fileURLWithPath:CommandLine.arguments[2])))!
precondition(ours.pixelsWide == 1024 && ours.pixelsHigh == 1024 && ios.pixelsWide == 1024 && !ios.hasAlpha)
func bounds(_ image:NSBitmapImageRep, mark:Bool)->[Int] {
    var x0=1024,y0=1024,x1=0,y1=0
    for y in 0..<1024 { for x in 0..<1024 {
        let c=image.colorAt(x:x,y:y)!.usingColorSpace(.deviceRGB)!
        let selected = mark ? c.alphaComponent > 0.99 && max(c.redComponent,c.greenComponent,c.blueComponent)-min(c.redComponent,c.greenComponent,c.blueComponent)>0.3 : c.alphaComponent > 0.99
        if selected {x0=min(x0,x);y0=min(y0,y);x1=max(x1,x);y1=max(y1,y)}
    } }
    return [x0,y0,x1,y1]
}
let tile=bounds(ours,mark:false), otherTile=bounds(reference,mark:false), mark=bounds(ours,mark:true), otherMark=bounds(reference,mark:true)
precondition(tile == otherTile,"Suite tile bounds differ")
precondition(zip(mark,otherMark).allSatisfy{abs($0-$1)<=1},"Suite mark scale/position differs")
print("PASS: Shot Reporter tile bounds \(tile), mark bounds \(mark); reference \(otherMark). iOS 1024px opaque RGB. Source logo remains separate and unchanged.")
