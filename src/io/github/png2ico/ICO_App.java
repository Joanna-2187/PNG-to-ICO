package io.github.png2ico;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

// Swing UI
public final class Ico_App extends JFrame {

    //UI pieces to update
    private final PreviewPanel preview = new PreviewPanel();
    private final JLabel status = new JLabel("Drop a PNG here or click Open.");
    private final JButton saveBtn = new JButton("Save as ICO…");
    private final List<JCheckBox> sizeBoxes = new ArrayList<>();
    //Currently loaded PNG
    private BufferedImage image;
    private File sourceFile;

    public Ico_App() {
        super("PNG to ICO Converter");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));
        ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        //Preview area
        add(preview, BorderLayout.CENTER);

        //Icon size, all ticked by default
        JPanel sizes = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        sizes.setBorder(BorderFactory.createTitledBorder("Icon sizes"));
        for (int s : Png2Ico.DEFAULT_SIZES) {
            JCheckBox cb = new JCheckBox(s + "px", true);
            cb.putClientProperty("size", s);
            sizeBoxes.add(cb);
            sizes.add(cb);
        }

        //Buttons
        JButton openBtn = new JButton("Open PNG…");
        openBtn.addActionListener(e -> chooseFile());
        saveBtn.setEnabled(false);
        saveBtn.addActionListener(e -> save());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(openBtn);
        buttons.add(saveBtn);

        //Bottom bar
        JPanel bottom = new JPanel(new BorderLayout(6, 6));
        bottom.add(sizes, BorderLayout.NORTH);
        bottom.add(status, BorderLayout.CENTER);
        bottom.add(buttons, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);

        //Drag and drop into preview area
        new DropTarget(preview, new DropTargetAdapter() {
            @Override @SuppressWarnings("unchecked")
            public void drop(DropTargetDropEvent ev) {
                try {
                    ev.acceptDrop(DnDConstants.ACTION_COPY);
                    List<File> files = (List<File>) ev.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);
                    if (!files.isEmpty()) load(files.get(0));
                    ev.dropComplete(true);
                } catch (Exception ex) {
                    ev.dropComplete(false);
                    setStatus("Drop failed: " + ex.getMessage());
                }
            }
        });

        setSize(480, 520);
        setMinimumSize(new Dimension(460, 400));
        setLocationRelativeTo(null);
    }

    //File picker limited to PNG files.
    private void chooseFile() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PNG images", "png"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) load(fc.getSelectedFile());
    }

    //Reads the image, shows it in the preview
    private void load(File f) {
        try {
            BufferedImage img = ImageIO.read(f);
            if (img == null) { setStatus("Not a readable image: " + f.getName()); return; }
            image = img;
            sourceFile = f;
            preview.setImage(img);
            saveBtn.setEnabled(true);
            String warn = img.getWidth() < 256 || img.getHeight() < 256
                    ? "  (smaller than 256px: large sizes will be upscaled)" : "";
            setStatus(f.getName() + " - " + img.getWidth() + "x" + img.getHeight() + warn);
        } catch (Exception ex) {
            setStatus("Error: " + ex.getMessage());
        }
    }

    //Asks where to save + convert
    private void save() {
        List<Integer> chosen = new ArrayList<>();
        for (JCheckBox cb : sizeBoxes) if (cb.isSelected()) chosen.add((Integer) cb.getClientProperty("size"));
        if (chosen.isEmpty()) { setStatus("Select at least one size."); return; }

        //Suggest icon name, same as PNG, with .ico on the end
        JFileChooser fc = new JFileChooser(sourceFile.getParentFile());
        fc.setSelectedFile(new File(sourceFile.getName().replaceFirst("(?i)\\.png$", "") + ".ico"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File out = fc.getSelectedFile();
        if (!out.getName().toLowerCase().endsWith(".ico")) out = new File(out.getPath() + ".ico");

        try {
            //Conversion
            int[] sizes = chosen.stream().mapToInt(Integer::intValue).toArray();
            Files.write(out.toPath(), Png2Ico.convert(image, sizes));
            setStatus("Saved " + out.getName() + " (" + sizes.length + " sizes)");
        } catch (Exception ex) {
            setStatus("Save failed: " + ex.getMessage());
        }
    }



    private void setStatus(String s) { status.setText(s); }

    //Shows the image over a checkerboard if transparent
    private static final class PreviewPanel extends JPanel {
        private BufferedImage img;
        PreviewPanel() {
            setBorder(BorderFactory.createDashedBorder(Color.GRAY, 4, 4));
            setPreferredSize(new Dimension(400, 300));
        }
        void setImage(BufferedImage i) { img = i; repaint(); }
        @Override protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            int w = getWidth(), h = getHeight();
            if (img == null) {
                //Nothing loaded yet: show a hint
                g.setColor(Color.GRAY);
                String t = "Drop PNG here";
                FontMetrics fm = g.getFontMetrics();
                g.drawString(t, (w - fm.stringWidth(t)) / 2, h / 2);
            } else {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                //Scale the image to fit the panel, keepi proportions
                double sc = Math.min((w - 20.0) / img.getWidth(), (h - 20.0) / img.getHeight());
                int dw = (int) (img.getWidth() * sc), dh = (int) (img.getHeight() * sc);
                int x = (w - dw) / 2, y = (h - dh) / 2;
                //Draw checkerboard behind the image
                int cell = 10;
                for (int cy = 0; cy < dh; cy += cell)
                    for (int cx = 0; cx < dw; cx += cell) {
                        g.setColor(((cx / cell + cy / cell) % 2 == 0) ? new Color(0xDDDDDD) : Color.WHITE);
                        g.fillRect(x + cx, y + cy, Math.min(cell, dw - cx), Math.min(cell, dh - cy));
                    }
                //Draw the image
                g.drawImage(img, x, y, dw, dh, null);
            }
            g.dispose();
        }
    }

    //Sarts the app on the Swing thread
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
            new Ico_App().setVisible(true);
        });
    }
}