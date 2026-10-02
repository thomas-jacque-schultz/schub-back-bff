package schultz.thomas.schub.bff.business.service;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

// L'aperçu d'un lien collé dans Discord : 1200 × 630, le format Open Graph. Couleurs de l'identité PremadeLab.
// Polices embarquées : une image sans fontconfig ne sait pas résoudre les polices logiques (SansSerif).
@Component
public class PlayerCardRenderer {

    private static final int LARGEUR = 1200;
    private static final int HAUTEUR = 630;
    private static final Color OBSIDIENNE = new Color(0x0A, 0x06, 0x0C);
    private static final Color PAPIER = new Color(0x15, 0x0D, 0x18);
    private static final Color OR = new Color(0xC9, 0xA2, 0x27);
    private static final Color PRUNE = new Color(0xA4, 0x47, 0x7E);
    private static final Color TEXTE = new Color(0xF2, 0xE9, 0xEE);
    private static final Color SECONDAIRE = new Color(0xB6, 0xA3, 0xB4);

    // Chargées à la première carte : le démarrage du BFF n'en dépend pas.
    private static final class Polices {
        static final Font REGULAR = charge("/fonts/DejaVuSans.ttf");
        static final Font BOLD = charge("/fonts/DejaVuSans-Bold.ttf");
    }

    static Font charge(String ressource) {
        try (InputStream in = PlayerCardRenderer.class.getResourceAsStream(ressource)) {
            return Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (IOException | FontFormatException e) {
            throw new IllegalStateException("Police introuvable : " + ressource, e);
        }
    }

    public byte[] render(String riotId, String rankLine, List<String> figures) {
        BufferedImage image = new BufferedImage(LARGEUR, HAUTEUR, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            g.setColor(OBSIDIENNE);
            g.fillRect(0, 0, LARGEUR, HAUTEUR);
            g.setColor(PAPIER);
            g.fillRoundRect(60, 60, LARGEUR - 120, HAUTEUR - 120, 8, 8);
            g.setColor(new Color(0xC9, 0xA2, 0x27, 60));
            g.setStroke(new BasicStroke(2f));
            g.drawRoundRect(60, 60, LARGEUR - 120, HAUTEUR - 120, 8, 8);

            // La marque : deux pastilles qui se chevauchent, le premade.
            g.setColor(PRUNE);
            g.fillOval(108, 112, 44, 44);
            g.setColor(new Color(0xC9, 0xA2, 0x27, 215));
            g.fillOval(132, 100, 44, 44);
            g.setColor(TEXTE);
            g.setFont(Polices.BOLD.deriveFont(34f));
            g.drawString("PremadeLab", 196, 142);

            g.setFont(ajuste(g, riotId, Polices.BOLD.deriveFont(76f), LARGEUR - 220));
            g.drawString(riotId, 110, 290);

            if (rankLine != null) {
                g.setColor(OR);
                g.setFont(Polices.BOLD.deriveFont(40f));
                g.drawString(rankLine, 110, 360);
            }

            g.setColor(SECONDAIRE);
            g.setFont(Polices.REGULAR.deriveFont(34f));
            int y = 440;
            for (String ligne : figures) {
                g.drawString(ligne, 110, y);
                y += 50;
            }

            g.setColor(OR);
            g.fillRect(110, HAUTEUR - 110, 80, 6);
        } finally {
            g.dispose();
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Font ajuste(Graphics2D g, String texte, Font font, int largeurMax) {
        Font courante = font;
        while (courante.getSize() > 36 && g.getFontMetrics(courante).stringWidth(texte) > largeurMax) {
            courante = courante.deriveFont((float) courante.getSize() - 4);
        }
        return courante;
    }
}
