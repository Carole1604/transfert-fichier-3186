import java.io.*;
import java.net.*;
import java.util.ArrayList;

public class Serveur {
    private String ip;
    private int port;
    private String path;

    public Serveur(int numeroServeur) {
        getConf(numeroServeur);
    }

    private void getConf(int numeroServeur) {
        String filePath = "serveur" + numeroServeur + "_conf.txt"; // Chemin vers votre fichier

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                // Ignorer les lignes vides ou mal formatées
                if (line.trim().isEmpty() || !line.contains("=")) {
                    continue;
                }

                // Découper la ligne en clé et valeur
                String[] parts = line.split("=", 2); // Diviser au niveau du premier "="
                String key = parts[0].trim(); // Partie avant "=" (clé)
                String value = parts[1].trim(); // Partie après "=" (valeur)

                // Afficher la clé et la valeur
                System.out.println("Clé : " + key + ", Valeur : " + value);

                if (key.equals("PORT")) {
                    this.port = Integer.valueOf(value);
                } else if (key.equals("IP")) {
                    this.ip = value;
                } else if (key.equals("DIRECTORY_PATH")) {
                    this.path = value;
                    File directory = new File(path);
                    if (!directory.exists()) {
                        directory.mkdirs();
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Erreur lors de la lecture du fichier : " + e.getMessage());
        }
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Serveur démarré sur " + ip + ":" + port);

            while (true) {
                try (Socket masterSocket = serverSocket.accept()) {
                    handleMaster(masterSocket);
                } catch (IOException e) {
                    System.err.println("Erreur lors de l'acceptation de la connexion : " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Erreur lors du démarrage du serveur : " + e.getMessage());
        }
    }

    private void handleMaster(Socket masterSocket) {
        try (
                DataInputStream dis = new DataInputStream(masterSocket.getInputStream());
                DataOutputStream dos = new DataOutputStream(masterSocket.getOutputStream())) {
            String requestType = dis.readUTF();

            switch (requestType) {
                case "LIST_FILES":
                    sendFileList(dos);
                    break;

                case "DELETE":
                    deleteFile(dis, dos);
                    break;
                case "DOWNLOAD":
                    System.out.println("Tentative de téléchargement sur les sous serveurs");
                    handleDownloadRequest(dis, dos);
                    break;

                case "UPLOAD":
                    String partName = dis.readUTF();
                    long partSize = dis.readLong();
                    handleFileReception(partName, partSize, dis);
                    break;

                default:
                    System.out.println("Requête non reconnue : " + requestType);
            }
        } catch (IOException e) {
            System.err.println("Erreur lors du traitement de la requête : " + e.getMessage());
        }
    }

    private void deleteFile(DataInputStream dis, DataOutputStream dos) {
        try {
            String nomFichier = dis.readUTF().trim();
            System.out.println("tentative de suppresion du fichier : " + nomFichier + " sur les sous serveurs");

            File fichier = new File(path + nomFichier);
            System.out.println("Chemin complet : " + fichier.getAbsolutePath());
            System.out.println("Fichier existe ? " + fichier.exists());

            if (fichier.exists()) {
                if (fichier.delete()) {
                    System.out.println("Fichier supprimé avec succès : " + nomFichier);
                    dos.writeUTF("DELETE_SUCCESS");
                } else {
                    System.out.println("Échec de suppression du fichier : " + nomFichier);
                    dos.writeUTF("DELETE_FAIL");
                }
            } else {
                System.out.println("Fichier non trouvé : " + nomFichier);
                dos.writeUTF("FILE_NOT_FOUND");
            }
        } catch (Exception e) {
            // TODO: handle exception
            System.out.println("Erreur : " + e.getMessage());
        }

    }

    private void handleDownloadRequest(DataInputStream dis, DataOutputStream dos) {
        try {
            String nomFichier = dis.readUTF();
            File fichier = new File(path, nomFichier);

            if (fichier.exists()) {
                dos.writeUTF("OK");
                dos.writeLong(fichier.length());

                try (FileInputStream fis = new FileInputStream(fichier)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        dos.write(buffer, 0, bytesRead);
                    }
                }

                System.out.println(nomFichier + " envoyé.");
            } else {
                dos.writeUTF("Fichier non trouvé.");
            }
        } catch (Exception e) {
            try {
                dos.writeUTF(e.getMessage());
            } catch (Exception ex) {
                System.out.println("Erreur : " + ex.getMessage());
            }
        }

    }

    private void sendFileList(DataOutputStream dos) throws IOException {
        File folder = new File(path);
        File[] files = folder.listFiles();

        if (files != null) {
            ArrayList<String> uniqueFiles = new ArrayList<>();
            for (File file : files) {
                String fileName = file.getName();
                if (fileName.startsWith("part1_")) {
                    uniqueFiles.add(fileName.replaceFirst("part1_", ""));
                }
            }

            dos.writeInt(uniqueFiles.size());
            for (String uniqueFile : uniqueFiles) {
                dos.writeUTF(uniqueFile);
            }
        } else {
            dos.writeInt(0);
        }
    }

    private void handleFileReception(String partName, long partSize, DataInputStream dis) throws IOException {
        File file = new File(path, partName);

        try (FileOutputStream fos = new FileOutputStream(file)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            long remaining = partSize;

            while ((bytesRead = dis.read(buffer, 0, (int) Math.min(buffer.length, remaining))) > 0) {
                fos.write(buffer, 0, bytesRead);
                remaining -= bytesRead;
            }
        }
        System.out.println("Fichier " + partName + " enregistré sur " + path);
    }
}
