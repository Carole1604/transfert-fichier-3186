import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.Socket;

public class ClientUpload {
    private static final String MASTER_IP = "localhost";
    private static final int MASTER_PORT = 6000;
    private File selectedFile;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ClientUpload::new);
    }

    public ClientUpload() {
        JFrame frame = new JFrame("Uploader un fichier");
        frame.setSize(400, 200);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new FlowLayout());

        JButton selectButton = new JButton("Sélectionner un fichier");
        selectButton.addActionListener(e -> selectFile());
        frame.add(selectButton);

        JButton sendButton = new JButton("Envoyer le fichier");
        sendButton.addActionListener(e -> sendFile());
        frame.add(sendButton);

        frame.setVisible(true);
    }

    private void selectFile() {
        JFileChooser fileChooser = new JFileChooser();
        int option = fileChooser.showOpenDialog(null);
        if (option == JFileChooser.APPROVE_OPTION) {
            selectedFile = fileChooser.getSelectedFile();
            JOptionPane.showMessageDialog(null, "Fichier sélectionné : " + selectedFile.getName());
        } else {
            JOptionPane.showMessageDialog(null, "Aucun fichier sélectionné.");
        }
    }

    private void sendFile() {
        if (selectedFile == null) {
            JOptionPane.showMessageDialog(null, "Veuillez d'abord sélectionner un fichier.", "Erreur", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try (Socket socket = new Socket(MASTER_IP, MASTER_PORT);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
             FileInputStream fis = new FileInputStream(selectedFile)) {

            dos.writeUTF("UPLOAD");
            dos.writeUTF(selectedFile.getName());
            dos.writeLong(selectedFile.length());

            byte[] buffer = new byte[4096];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                dos.write(buffer, 0, read);
            }

            JOptionPane.showMessageDialog(null, "Fichier envoyé avec succès.");
            selectedFile = null; // Réinitialiser après l'envoi.
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Erreur lors de l'envoi du fichier.", "Erreur", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }
}
