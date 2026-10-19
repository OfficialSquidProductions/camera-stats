import com.android.apksig.internal.apk.AndroidBinXmlParser;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;

/** Read-only inspection of the built APK's binary manifest, using pinned apksig 8.9.2. */
public final class InspectAndroidRelease {
    public static void main(String[] args) throws Exception {
        if(args.length!=1) throw new IllegalArgumentException("release.apk");
        byte[] bytes;
        try(var zip=new ZipFile(args[0])) {var entry=zip.getEntry("AndroidManifest.xml");if(entry==null || entry.getSize()>1048576) throw new IllegalArgumentException("Missing/oversized manifest");try(var in=zip.getInputStream(entry)){bytes=in.readNBytes(1048577);}}
        var xml=new AndroidBinXmlParser(ByteBuffer.wrap(bytes));boolean manifest=false,sdk=false,application=false;
        var permissions=new ArrayList<String>();
        while(xml.next()!=AndroidBinXmlParser.EVENT_END_DOCUMENT) {
            if(xml.getEventType()!=AndroidBinXmlParser.EVENT_START_ELEMENT) continue;
            var attrs=new HashMap<String,String>();
            for(int i=0;i<xml.getAttributeCount();i++) {
                String value=switch(xml.getAttributeValueType(i)) {
                    case AndroidBinXmlParser.VALUE_TYPE_STRING -> xml.getAttributeStringValue(i);
                    case AndroidBinXmlParser.VALUE_TYPE_INT -> Integer.toString(xml.getAttributeIntValue(i));
                    case AndroidBinXmlParser.VALUE_TYPE_BOOLEAN -> Boolean.toString(xml.getAttributeBooleanValue(i));
                    default -> "resource";
                };
                attrs.put(xml.getAttributeName(i),value);
            }
            switch(xml.getName()) {
                case "manifest" -> {require("org.squidproductions.camerastats".equals(attrs.get("package")),"Unexpected package");require("2".equals(attrs.get("versionCode")) && "0.1.1".equals(attrs.get("versionName")),"Unexpected release version");manifest=true;}
                case "uses-sdk" -> {require("26".equals(attrs.get("minSdkVersion")) && "35".equals(attrs.get("targetSdkVersion")),"Unexpected SDK range");sdk=true;}
                case "application" -> {require("Shot Reporter".equals(attrs.get("label")),"Unexpected display name");require(!"true".equals(attrs.get("debuggable")),"Release must not be debuggable");require("false".equals(attrs.get("allowBackup")),"OS backup must be disabled");application=true;}
                case "uses-permission", "uses-permission-sdk-23" -> permissions.add(attrs.get("name"));
            }
        }
        require(manifest && sdk && application,"Incomplete manifest");
        for(String permission:permissions) require("org.squidproductions.camerastats.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION".equals(permission),"Unexpected permission: "+permission);
        System.out.println("PASS: Shot Reporter 0.1.1 (2), minimum API 26, target API 35, release not debuggable, OS backup disabled; no Internet/camera/microphone/broad-storage permission.");
        System.out.println("Manifest permissions: "+permissions);
    }
    private static void require(boolean value,String message) {if(!value) throw new IllegalStateException(message);}
}
