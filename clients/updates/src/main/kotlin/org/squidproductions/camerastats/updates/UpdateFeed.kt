package org.squidproductions.camerastats.updates

import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import java.net.URI
import java.security.*
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.util.Base64

const val APPLICATION_ID="org.squidproductions.camerastats"
const val MSI_UPGRADE_CODE="c3774fda-7962-4264-a9bc-c428176d12b4"
val updateJSON=Json {ignoreUnknownKeys=false;encodeDefaults=true}
@Serializable data class SignedEnvelope(val payload:String,val signature:String)
@Serializable data class UpdateAsset(val url:String,val size:Long,val sha256:String,val upgradeCode:String,val signerSHA256:String,val msiVersion:String)
@Serializable data class UpdateRelease(val version:String,val build:Long,val channel:String,val releaseNotes:String,val windowsX64:UpdateAsset?=null)
@Serializable data class UpdateFeed(val schema:Int,val appID:String,val generatedAt:String,val expiresAt:String,val releases:List<UpdateRelease>)
/** Only bundled configuration is trusted. A feed cannot rotate its own key, hosts or publisher. */
@Serializable data class UpdatePolicy(val feedURL:String,val publicKey:String,val allowedHosts:Set<String>,val signerSHA256:String,val currentVersion:String,val currentBuild:Long,val defaultChannel:String="alpha",val currentMSIVersion:String="0.1.1") {
    fun validate() {
        check(allowedHosts.isNotEmpty() && allowedHosts.all {it==it.lowercase() && Regex("[a-z0-9.-]+").matches(it)}) {"Update download hosts are not configured."}
        trustedURI(feedURL);key();check(Regex("[a-fA-F0-9]{64}").matches(signerSHA256)) {"A dedicated Windows publisher certificate is not configured."}
        Version.parse(currentVersion);validateMSIVersion(currentMSIVersion);check(currentBuild>0);check(defaultChannel in setOf("alpha","stable"))
    }
    fun trustedURI(value:String):URI {
        val uri=URI(value)
        check(uri.scheme=="https" && uri.host?.lowercase() in allowedHosts && uri.userInfo==null && uri.fragment==null && uri.port in setOf(-1,443)) {"Update address is outside the bundled HTTPS trust policy."}
        return uri
    }
    fun key():PublicKey=KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKey)))
}
fun validateMSIVersion(value:String) {
    val version=Version.parse(value)
    check(version.pre.isEmpty() && !value.contains('+') && version.major<=255 && version.minor<=255 && version.patch<=65535) {"MSI ProductVersion is outside Windows Installer bounds."}
}
/** Strict SemVer, including prerelease ordering; build is an additional monotonic rollback guard. */
data class Version(val major:Long,val minor:Long,val patch:Long,val pre:List<String>):Comparable<Version> {
    override fun compareTo(other:Version):Int {
        listOf(major to other.major,minor to other.minor,patch to other.patch).forEach {(a,b)->if(a!=b) return a.compareTo(b)}
        if(pre.isEmpty() || other.pre.isEmpty()) return when {pre.isEmpty() && other.pre.isEmpty()->0;pre.isEmpty()->1;else->-1}
        pre.zip(other.pre).forEach {(a,b)->
            val an=a.toLongOrNull();val bn=b.toLongOrNull();val result=when {an!=null && bn!=null->an.compareTo(bn);an!=null->-1;bn!=null->1;else->a.compareTo(b)}
            if(result!=0) return result
        };return pre.size.compareTo(other.pre.size)
    }
    companion object {
        fun parse(value:String):Version {
            val match=Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?").matchEntire(value) ?: error("Invalid release version.")
            val pre=match.groupValues[4].takeIf {it.isNotEmpty()}?.split('.') ?: emptyList()
            check(pre.none {it.all(Char::isDigit) && it.length>1 && it.startsWith('0')}) {"Invalid numeric prerelease version."}
            return Version(match.groupValues[1].toLong(),match.groupValues[2].toLong(),match.groupValues[3].toLong(),pre)
        }
    }
}
fun verifyFeed(bytes:ByteArray,policy:UpdatePolicy,channel:String,now:Instant=Instant.now(),highestBuild:Long=policy.currentBuild):UpdateRelease? {
    policy.validate();check(channel in setOf("alpha","stable"));check(bytes.size<=1024*1024) {"Update feed exceeds 1 MiB."}
    val envelope=updateJSON.decodeFromString<SignedEnvelope>(bytes.toString(Charsets.UTF_8))
    val payload=Base64.getDecoder().decode(envelope.payload);check(payload.size<=512*1024)
    check(Signature.getInstance("Ed25519").run {initVerify(policy.key());update(payload);verify(Base64.getDecoder().decode(envelope.signature))}) {"Update feed signature is invalid."}
    val feed=updateJSON.decodeFromString<UpdateFeed>(payload.toString(Charsets.UTF_8))
    check(feed.schema==1 && feed.appID==APPLICATION_ID) {"This feed belongs to a different app or schema."}
    val generated=Instant.parse(feed.generatedAt);val expires=Instant.parse(feed.expiresAt)
    check(!generated.isAfter(now.plusSeconds(300)) && expires.isAfter(now) && expires.isAfter(generated) && expires<=generated.plusSeconds(31L*86400)) {"Update feed is expired or has invalid dates."}
    check(feed.releases.size<=100 && feed.releases.map {it.build}.distinct().size==feed.releases.size)
    feed.releases.forEach {r->
        val v=Version.parse(r.version);check(r.build>0 && r.channel in setOf("alpha","stable") && r.releaseNotes.length<=100000)
        check(r.channel!="stable" || v.pre.isEmpty()) {"A prerelease cannot enter the stable channel."}
        r.windowsX64?.let {a->policy.trustedURI(a.url);check(a.size in 1..4_294_967_296L && Regex("[a-fA-F0-9]{64}").matches(a.sha256));check(a.upgradeCode.equals(MSI_UPGRADE_CODE,true) && a.signerSHA256.equals(policy.signerSHA256,true)) {"Unexpected MSI identity or publisher."};check(URI(a.url).path.endsWith(".msi",true));validateMSIVersion(a.msiVersion)}
    }
    return feed.releases.filter {it.windowsX64!=null && Version.parse(it.windowsX64.msiVersion)>Version.parse(policy.currentMSIVersion) && (channel=="alpha" || it.channel=="stable") && it.build>policy.currentBuild && it.build>=highestBuild && Version.parse(it.version)>Version.parse(policy.currentVersion)}.maxByOrNull {it.build}
}
