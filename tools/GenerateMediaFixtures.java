import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.util.*;

/** Fictional test footage only; writes a separate output directory, never imports user media. */
public class GenerateMediaFixtures {
    public static void main(String[] args) throws Exception {
        if(args.length!=2) throw new IllegalArgumentException("ffmpeg executable; output MP4");
        Path output=Path.of(args[1]).toAbsolutePath();Files.createDirectories(output.getParent());
        if(Files.exists(output)) throw new IllegalStateException("Fixture already exists; inspect before regenerating.");
        Path frames=Files.createTempDirectory("shot-reporter-numbered-frames-");
        try {
            for(int n=0;n<24;n++) {
                BufferedImage image=new BufferedImage(640,360,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();
                g.setColor(n<12?new Color(230,20,40):new Color(20,40,230));g.fillRect(0,0,640,360);
                g.setColor(new Color(245,245,225));g.fillRect(90,65,460,230);g.setColor(Color.BLACK);g.fillRect(90,65,460,35);
                for(int x=100;x<540;x+=45) {g.setColor(Color.WHITE);g.fillRect(x,68,24,28);}
                g.setFont(new Font("Dialog",Font.BOLD,22));g.setColor(Color.BLACK);g.drawString("THE LAST LIGHT · SYNTHETIC",110,135);g.drawString("SCENE 3A     TAKE 2     CAM A",110,180);
                g.setFont(new Font("Dialog",Font.BOLD,32));g.drawString(String.format("SOURCE FRAME %02d",n),125,245);g.dispose();
                ImageIO.write(image,"png",frames.resolve(String.format("frame%03d.png",n)).toFile());
            }
            var command=java.util.List.of(args[0],"-hide_banner","-nostdin","-v","error","-protocol_whitelist","file,pipe","-framerate","12","-i",frames.resolve("frame%03d.png").toString(),"-c:v","mpeg4","-bf","2","-q:v","2","-pix_fmt","yuv420p","-metadata","title=The Last Light synthetic frame fixture","-f","mp4",output.toString());
            Process p=new ProcessBuilder(command).inheritIO().start();if(p.waitFor()!=0) throw new IllegalStateException("Synthetic fixture encode failed");
        } finally {try(var files=Files.list(frames)){for(Path file:files.toList()) Files.delete(file);}Files.delete(frames);}
        System.out.println("Created 24-frame, 12 fps, MPEG-4 Part 2 MP4 with B frames. Synthetic slate and colors identify presentation-order frames. No camera support inferred.");
    }
}
