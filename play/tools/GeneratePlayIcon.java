import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public final class GeneratePlayIcon {
    private GeneratePlayIcon() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Output path required");
        var image = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
        var canvas = image.createGraphics();
        canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        canvas.setColor(Color.WHITE);
        canvas.fillRect(0, 0, 512, 512);
        canvas.setStroke(new BasicStroke(80, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        var blue = new Path2D.Double();
        blue.moveTo(118, 341);
        blue.lineTo(118, 176);
        blue.curveTo(118, 138, 142, 129, 170, 157);
        blue.lineTo(256, 251);
        canvas.setColor(Color.decode("#078AF0"));
        canvas.draw(blue);

        var green = new Path2D.Double();
        green.moveTo(256, 251);
        green.lineTo(346, 152);
        green.curveTo(374, 124, 394, 143, 394, 180);
        green.lineTo(394, 341);
        canvas.setColor(Color.decode("#62B512"));
        canvas.draw(green);

        canvas.setColor(Color.decode("#071B37"));
        canvas.fillOval(228, 313, 38, 38);
        canvas.fillOval(186, 337, 28, 28);
        canvas.fillOval(280, 337, 28, 28);
        canvas.fill(new RoundRectangle2D.Double(228, 355, 38, 81, 28, 28));
        canvas.fill(new RoundRectangle2D.Double(186, 369, 28, 67, 22, 22));
        canvas.fill(new RoundRectangle2D.Double(280, 369, 28, 67, 22, 22));
        canvas.dispose();
        ImageIO.write(image, "png", new File(args[0]));
    }
}
