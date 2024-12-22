import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;

public class UnifiedServer {
    private static int PORT;
    private static List<ServerInfo> sousServeurs = new ArrayList<>();

    public static void main(String[] args) {
        if (!getConf("master_serveur_conf.txt")) {
            System.err.println("Échec du chargement de la configuration. Arrêt du serveur.");
            return;
        }
        System.out.println("Serveur en écoute sur le port " + PORT);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                // Démarrage d'un thread pour gérer le client
                new Thread(() -> handleClient(clientSocket)).start();
            }
        } catch (IOException e) {
            System.out.println("Erreur du serveur : " + e.getMessage());
        }
    }

    private static boolean getConf(String filePath) {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            boolean isMasterConfig = false;
            boolean isSubServerConfig = false;
    
            while ((line = br.readLine()) != null) {
                line = line.trim();
    
                // Ignorer les lignes vides ou commentaires
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
    
                // Vérifier les sections
                if (line.equals("[MASTER FONGIGURATION]")) {
                    isMasterConfig = true;
                    isSubServerConfig = false;
                    continue;
                } else if (line.equals("[SOUS SERVEUR CONFIGURATION]")) {
                    isMasterConfig = false;
                    isSubServerConfig = true;
                    continue;
                }
    
                // Lecture des configurations de la section Master
                if (isMasterConfig && line.startsWith("PORT=")) {
                    PORT = Integer.parseInt(line.split("=")[1].trim());
                }
    
                // Lecture des configurations des sous-serveurs
                if (isSubServerConfig && line.startsWith("[serveur")) {
                    // Lire les informations du sous-serveur
                    String ip = null;
                    int port = 0;
    
                    while ((line = br.readLine()) != null) {
                        line = line.trim();
                        if (line.startsWith("IP")) {
                            ip = line.split("=")[1].trim();
                        } else if (line.startsWith("PORT")) {
                            port = Integer.parseInt(line.split("=")[1].trim());
                        } else if (line.startsWith("[") || line.isEmpty()) {
                            // Fin de la section du serveur ou début d'une nouvelle
                            break;
                        }
                    }
    
                    // Ajouter le serveur si les données sont valides
                    if (ip != null && port != 0) {
                        ServerInfo serv = new ServerInfo(ip, port);
                        System.out.println(serv.toString());
                        sousServeurs.add(new ServerInfo(ip, port));
                    }
                }
            }
    
            // Validation de la configuration
            if (PORT == 0) {
                System.err.println("Erreur : PORT principal non configuré.");
                return false;
            }
            if (sousServeurs.isEmpty()) {
                System.err.println("Erreur : Aucun sous-serveur configuré.");
                return false;
            }
    
            return true;
        } catch (IOException | NumberFormatException e) {
            System.err.println("Erreur lors de la lecture de la configuration : " + e.getMessage());
            return false;
        }
    }
    
    
    private static void setPort(int port) {
        PORT = port;
    }

    private static void handleClient(Socket clientSocket) {
        try (
                DataInputStream dis = new DataInputStream(clientSocket.getInputStream());
                DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream())) {
            // Recevoir l'action du client : UPLOAD ou DOWNLOAD
            String action = dis.readUTF();
            System.out.println("Comande recu : " + action);
            if ("UPLOAD".equalsIgnoreCase(action)) {
                handleUpload(dis, dos);
            } else if ("DOWNLOAD".equalsIgnoreCase(action)) {
                handleDownload(dis, dos);
            } else if ("LIST".equalsIgnoreCase(action)) {
                handleList(dos);
            } else if ("DELETE".equalsIgnoreCase(action)) {
                handleDelete(dis, dos);
            }

            else {
                dos.writeUTF("Action inconnue.");
            }
        } catch (IOException e) {
            System.out.println("Erreur avec le client : " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.out.println("Erreur lors de la fermeture du socket : " + e.getMessage());
            }
        }
    }

    private static void handleDownload(DataInputStream dis, DataOutputStream dos) {
        try {
            // Lire le nom du fichier demandé par le client
            String nomFichier = dis.readUTF();
            System.out.println("Demande de téléchargement pour : " + nomFichier);

            // Tableau pour stocker les fichiers téléchargés
            File[] fichiers = new File[sousServeurs.size()];

            // Télécharger chaque partie depuis les serveurs
            for (int i = 0; i < sousServeurs.size(); i++) {
                String serverIP = sousServeurs.get(i).ip;
                int serverPort = sousServeurs.get(i).port;

                try (Socket socket = new Socket(serverIP, serverPort);
                     DataOutputStream remoteDos = new DataOutputStream(socket.getOutputStream());
                     DataInputStream remoteDis = new DataInputStream(socket.getInputStream())) {

                    // Envoyer la commande de téléchargement
                    remoteDos.writeUTF("DOWNLOAD");
                    remoteDos.writeUTF("part" + (i + 1) + "_" + nomFichier);

                    // Réponse du serveur
                    String reponse = remoteDis.readUTF();
                    if ("OK".equals(reponse)) {
                        System.out.println("Téléchargement de la partie " + (i + 1) + " depuis " + serverIP);

                        // Lire la taille du fichier
                        long fileSize = remoteDis.readLong();
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        long bytesRemaining = fileSize;

                        // Écrire le fichier localement
                        File fichierLocal = new File("part" + (i + 1) + "_" + nomFichier);
                        fichiers[i] = fichierLocal;

                        try (FileOutputStream fos = new FileOutputStream(fichierLocal)) {
                            while (bytesRemaining > 0 && (bytesRead = remoteDis.read(buffer, 0, (int) Math.min(buffer.length, bytesRemaining))) != -1) {
                                fos.write(buffer, 0, bytesRead);
                                bytesRemaining -= bytesRead;
                            }
                        }

                        System.out.println("Partie " + (i + 1) + " téléchargée avec succès.");
                    } else {
                        
                        System.err.println("Erreur sur le serveur " + serverIP + ": " + reponse);
                        return;
                    }
                } catch (IOException ex) {
                    System.err.println("Erreur lors de la connexion au serveur " + serverIP + ": " + ex.getMessage());
                }
            }

            // Fusionner les fichiers
            File fichierFinal = new File(nomFichier);
            try (FileOutputStream fos = new FileOutputStream(fichierFinal)) {
                for (File fichier : fichiers) {
                    if (fichier != null) {
                        try (FileInputStream fis = new FileInputStream(fichier)) {
                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            while ((bytesRead = fis.read(buffer)) != -1) {
                                fos.write(buffer, 0, bytesRead);
                            }
                        }
                        fichier.delete(); // Supprimer la partie locale après la fusion
                    }
                }
            }

            System.out.println("Fichier final créé : " + fichierFinal.getAbsolutePath());

            // Envoyer le fichier au client
            try (FileInputStream fis = new FileInputStream(fichierFinal)) {
                dos.writeUTF("OK");
                dos.writeLong(fichierFinal.length());
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    dos.write(buffer, 0, bytesRead);
                }
            }

            System.out.println("Fichier envoyé au client.");
            fichierFinal.delete(); // Supprimer le fichier final local après envoi

        } catch (Exception e) {
            System.err.println("Erreur lors du traitement de la demande : " + e.getMessage());
        }
    }
    private static void handleDelete(DataInputStream dis, DataOutputStream dos) {
        System.out.println("delete");
        try {
            String nomFichier = dis.readUTF();
            System.out.println("tentive de suppresion du fichier : " + nomFichier);
            boolean delete = true;
            for (int i = 0; i < sousServeurs.size() ;i++) {
                String serverIP = sousServeurs.get(i).ip;
                int serverPort = sousServeurs.get(i).port;

                System.out.println("Connexion au serveur : " + serverIP + " sur le port " + serverPort);

                // Créer une connexion socket avec le serveur distant
                try (Socket socket = new Socket(serverIP, serverPort);
                        DataOutputStream remoteDos = new DataOutputStream(socket.getOutputStream());
                        DataInputStream remoteDis = new DataInputStream(socket.getInputStream())) {

                    // Envoyer la commande et le nom du fichier
                    String fichierSupprimer = "part" + (i + 1) + "_" + nomFichier;
                    System.out.println("suppresion du part de fichier : " + fichierSupprimer);

                    // envoyer la comande de suppresion
                    remoteDos.writeUTF("DELETE");
                    remoteDos.writeUTF(fichierSupprimer);

                    // recevoir la reponse du serveur
                    String reponse = remoteDis.readUTF();

                    if (reponse.equals("DELETE_SUCCESS")) {

                    } else if (reponse.equals("DELETE_FAIL")) {
                        delete = false;
                    } else if (reponse.equals("FILE_NOT_FOUND")) {
                        delete = false;
                    }
                    if (!delete) {
                        dos.writeUTF("Erreur de suppresion : " + reponse);
                        return;
                    }

                } catch (IOException ex) {
                    dos.writeUTF("Erreur : " + ex.getMessage());
                    System.err.println("Erreur lors de la connexion au serveur " + serverIP + ": " + ex.getMessage());
                }
            }
            dos.writeUTF("Suppresion du fichier : " + nomFichier + " reussi");
        } catch (Exception e) {
            System.out.println("Erreur : " + e.getMessage());
        }

    }

    private static void handleUpload(DataInputStream dis, DataOutputStream dos) {
        try {
            String fileName = dis.readUTF();
            long fileSize = dis.readLong();

            System.out.println("Fichier reçu : " + fileName + " (" + fileSize + " octets)");

            // Lire tout le fichier en mémoire
            byte[] fileContent = new byte[(int) fileSize];
            dis.readFully(fileContent);

            // Diviser le fichier en parties dynamiquement selon le nombre de serveurs
            int serverCount = sousServeurs.size();
            int partSize = (int) fileSize / serverCount;
            int remaining = (int) fileSize % serverCount;

            // Indicateur pour vérifier si toutes les parties ont été envoyées avec succès
            boolean uploadSuccess = true;

            for (int i = 0; i < serverCount; i++) {
                try {
                    int currentPartSize = partSize + (i == serverCount - 1 ? remaining : 0);
                    byte[] part = new byte[currentPartSize];

                    System.arraycopy(fileContent, i * partSize, part, 0, currentPartSize);

                    // Envoyer la partie au serveur correspondant
                    String partName = "part" + (i + 1) + "_" + fileName;
                    sendPartToServer(sousServeurs.get(i).ip, sousServeurs.get(i).port, partName, part);

                    System.out.println("Partie " + (i + 1) + " envoyée avec succès.");
                } catch (Exception e) {
                    uploadSuccess = false;
                    System.err.println("Erreur lors de l'envoi de la partie " + (i + 1) + ": " + e.getMessage());
                }
            }

            // Informer le client du résultat global
            if (uploadSuccess) {
                dos.writeUTF("Le fichier a été téléchargé et distribué avec succès.");
            } else {
                dos.writeUTF("Le fichier n'a pas pu être distribué complètement.");
            }

        } catch (IOException e) {
            try {
                dos.writeUTF("Une erreur est survenue lors du téléchargement du fichier : " + e.getMessage());
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            e.printStackTrace();
        }
    }

    private static boolean sendPartToServer(String serverIp, int serverPort, String partName, byte[] partData) {
        try (
                Socket socket = new Socket(serverIp, serverPort);
                DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {
            dos.writeUTF("UPLOAD");
            dos.writeUTF(partName);
            dos.writeLong(partData.length);
            dos.write(partData);

            System.out.println("Partie envoyée : " + partName + " (" + partData.length + " octets) au serveur : "
                    + serverIp + ":" + serverPort);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    private static void handleList(DataOutputStream dos) {
        System.out.println("liste");
        String serverIp = sousServeurs.get(0).ip;
        int serverPort = sousServeurs.get(0).port;

        ArrayList<String> fileList = fetchFileListFromServer(serverIp, serverPort);
        try {
            dos.writeInt(fileList.size());
            for (String fileName : fileList) {
                dos.writeUTF(fileName);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static ArrayList<String> fetchFileListFromServer(String serverIp, int serverPort) {
        ArrayList<String> fileList = new ArrayList<>();
        try (
                Socket socket = new Socket(serverIp, serverPort);
                DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                DataInputStream dis = new DataInputStream(socket.getInputStream())) {
            dos.writeUTF("LIST_FILES");

            int fileCount = dis.readInt();
            for (int i = 0; i < fileCount; i++) {
                fileList.add(dis.readUTF());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return fileList;
    }
    private static class ServerInfo {
        String ip;
        int port;

        public ServerInfo(String ip, int port) {
            this.ip = ip;
            this.port = port;
        }
        @Override
        public String toString() {
            return "Serveur{" + "ip='" + ip + '\'' + ", port=" + port + '}';
        }
    }
}
