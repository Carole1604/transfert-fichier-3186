import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;

public class ClientList {
    static final String MASTER_IP = "localhost";
    static final int MASTER_PORT = 6000;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new FileListFrame().setVisible(true));
    }
}

class FileListFrame extends JFrame {
    private JList<String> fileList;
    private JTextField fileNameField;
    private JButton downloadButton;
    private JButton deleteButton;

    public FileListFrame() {
        setTitle("Liste des fichiers");
        setSize(400, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Liste des fichiers
        fileList = new JList<>();
        add(new JScrollPane(fileList), BorderLayout.CENTER);

        // Ajouter un écouteur pour les clics sur les éléments de la liste
        fileList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selectedValue = fileList.getSelectedValue();
                if (selectedValue != null) {
                    fileNameField.setText(selectedValue);
                }
            }
        });

        // Panneau pour les actions
        JPanel actionPanel = new JPanel();
        actionPanel.setLayout(new BorderLayout());

        JPanel inputPanel = new JPanel();
        inputPanel.setLayout(new BorderLayout());

        fileNameField = new JTextField();
        fileNameField.setEditable(false); // Désactiver la saisie manuelle
        JLabel fileLabel = new JLabel("Nom du fichier :");
        inputPanel.add(fileLabel, BorderLayout.WEST);
        inputPanel.add(fileNameField, BorderLayout.CENTER);

        actionPanel.add(inputPanel, BorderLayout.NORTH);

        // Boutons Télécharger et Supprimer
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new GridLayout(1, 2));

        downloadButton = new JButton("Télécharger");
        deleteButton = new JButton("Supprimer");
        
        buttonPanel.add(downloadButton);
        buttonPanel.add(deleteButton);

        actionPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        add(actionPanel, BorderLayout.SOUTH);

        // Ajouter l'action au bouton Télécharger
        downloadButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String nomFichier = fileNameField.getText();
                if (nomFichier.isEmpty()) {
                    JOptionPane.showMessageDialog(FileListFrame.this, "Le champ du nom du fichier est vide.", "Erreur", JOptionPane.WARNING_MESSAGE);
                    
                } else {
                    System.out.println("nom du fichier = " + nomFichier);
                    dowloadFile(nomFichier);
                }
            }
        });

        fetchFileList();
    }

    private void fetchFileList() {
        try (Socket socket = new Socket(ClientList.MASTER_IP, ClientList.MASTER_PORT);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
             DataInputStream dis = new DataInputStream(socket.getInputStream())) {

            dos.writeUTF("LIST_FILES");

            int fileCount = dis.readInt();
            ArrayList<String> files = new ArrayList<>();
            for (int i = 0; i < fileCount; i++) {
                files.add(dis.readUTF());
            }

            fileList.setListData(files.toArray(new String[0]));
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Erreur lors de la récupération de la liste des fichiers.",
                    "Erreur", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }
    private void dowloadFile(String nomFichier){
        try (Socket socket = new Socket(ClientList.MASTER_IP, ClientList.MASTER_PORT);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
             DataInputStream dis = new DataInputStream(socket.getInputStream())) {
    
            dos.writeUTF("DOWLOAD"); // Envoie la commande au serveur
            dos.writeUTF(nomFichier); // Envoie le nom du fichier au serveur
    
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Erreur de connexion.",
                    "Erreur", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }        
    }
    
}
