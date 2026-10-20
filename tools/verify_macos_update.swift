#!/usr/bin/env swift
// Public-key-only verification of Sparkle 2.10 signed feeds and exact artifacts.
// No Keychain access or private key import/export. Synthetic self-test keys stay in memory.
import Foundation
import CryptoKit
struct Invalid:Error,CustomStringConvertible {let description:String;init(_ message:String){description=message}}
func verifyArtifact(_ bytes:Data,key:Curve25519.Signing.PublicKey,signature:String)throws {
    guard let signatureBytes=Data(base64Encoded:signature),signatureBytes.count==64,signatureBytes.base64EncodedString()==signature,key.isValidSignature(signatureBytes,for:bytes) else {throw Invalid("Artifact signature verification failed")}
}
func feedContent(_ bytes:Data,key:Curve25519.Signing.PublicKey)throws->Data {
    guard bytes.count<=2*1024*1024 else {throw Invalid("Feed exceeds verification limit")}
    let prefix=Data("<!-- sparkle-signatures:\n".utf8)
    guard let block=bytes.range(of:prefix,options:.backwards),bytes[..<block.lowerBound].range(of:prefix)==nil,let suffix=String(data:bytes[block.lowerBound...],encoding:.utf8) else {throw Invalid("Expected exactly one trailing signing block")}
    let lines=suffix.components(separatedBy:"\n")
    guard lines.count==5,lines[0]=="<!-- sparkle-signatures:",lines[1].hasPrefix("edSignature: "),lines[2].hasPrefix("length: "),lines[3]=="-->",lines[4].isEmpty else {throw Invalid("Noncanonical signing block")}
    let content=Data(bytes[..<block.lowerBound]),length=String(lines[2].dropFirst(8))
    guard length==String(content.count) else {throw Invalid("Signed byte length mismatch")}
    try verifyArtifact(content,key:key,signature:String(lines[1].dropFirst(13)))
    return content
}
func expectRejection(_ action:()throws->Void)throws {
    var rejected=false;do {try action()} catch {rejected=true}
    guard rejected else {throw Invalid("Invalid fixture accepted")}
}
func selfTest()throws {
    let key=Curve25519.Signing.PrivateKey(),content=Data("<?xml version=\"1.0\"?><rss><channel><title>Fixture</title></channel></rss>\n".utf8)
    let signature=try key.signature(for:content).base64EncodedString(),block=Data("<!-- sparkle-signatures:\nedSignature: \(signature)\nlength: \(content.count)\n-->\n".utf8),signed=content+block
    guard try feedContent(signed,key:key.publicKey)==content else {throw Invalid("Valid fixture rejected")}
    var changed=signed;changed[10] ^= 1
    let malformed=[content,changed,signed+Data("trailing".utf8),signed+block,content+Data(String(decoding:block,as:UTF8.self).replacingOccurrences(of:"length: \(content.count)",with:"length: 0").utf8)]
    for data in malformed {try expectRejection {_=try feedContent(data,key:key.publicKey)}}
    try expectRejection {_=try feedContent(signed,key:Curve25519.Signing.PrivateKey().publicKey)}
    try verifyArtifact(content,key:key.publicKey,signature:signature)
    try expectRejection {try verifyArtifact(content+Data([0]),key:key.publicKey,signature:signature)}
    print("PASS: valid feed/artifact; missing, tampered, trailing, duplicate, length-mismatch, wrong-key and changed-artifact rejection")
}
do {
    let args=CommandLine.arguments
    if args.count==2,args[1]=="--self-test" {try selfTest()}
    else {
        guard args.count==4 || args.count==5,args[1]=="--public-key",let raw=Data(base64Encoded:args[2]),raw.count==32 else {throw Invalid("Usage: verify_macos_update.swift --public-key BASE64 FILE [SIGNATURE], or --self-test")}
        let key=try Curve25519.Signing.PublicKey(rawRepresentation:raw),bytes=try Data(contentsOf:URL(fileURLWithPath:args[3]))
        if args.count==5 {try verifyArtifact(bytes,key:key,signature:args[4])} else {_=try feedContent(bytes,key:key)}
        print("VERIFIED")
    }
} catch {FileHandle.standardError.write(Data(("Verification failed: \(error)\n").utf8));exit(1)}
