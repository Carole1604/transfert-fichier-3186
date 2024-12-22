import java.util.Scanner;
import java.io.*;
import java.net.*;

/**
 * Client
 */
public class Client {

    public static void main(String[] args) {
        Scanner ecoute = new Scanner(System.in);
        System.out.println("Entrer l ip du serveur");
        String IP = ecoute.nextLine();

        System.out.println("Entrer le port du serveur");
        String PORT = ecoute.nextLine();
        boolean connexion = true;

        while (connexion) {
            try (Socket socket = new Socket(IP, Integer.valueOf(PORT));
                    DataInputStream dis = new DataInputStream(socket.getInputStream());
                    DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

                System.out.println("dulf>>Commande valider : download/upload/list/delete");
                String cmd = ecoute.nextLine();
                String[] parts = cmd.split(" ");
                parts[0] = parts[0].toUpperCase();
                cmd = parts[0];
                System.out.println("Commande entrer : " + cmd);
                if (cmd.equals("EXIT")) {
                    connexion = false;
                } else if (cmd.equals("UPLOAD")) {
                    dos.writeUTF(cmd);
                    uplaodFile(parts, dis, dos);
                } else if (cmd.equals("DOWNLOAD")) {
                    dowloadFile(parts, dis, dos);
                } else if (cmd.equals("LIST")) {
                    afficheListeFichier(dis, dos);
                } else if (cmd.equals("DELETE")) {

                    deleteFile(parts, dis, dos);
                } else {
                    System.out.println("Commande inconnue");
                }
                socket.close();
            } catch (IOException ex) {
                connexion = false;
                System.out.println("Erreur de connexion au serveur : " + ex.getMessage());
            }
        }
    }

    public static void dowloadFile(String[] commande, DataInputStream dis, DataOutputStream dos) {
        Scanner ecoute = new Scanner(System.in);
        if (commande.length != 3) {
            System.out.println("commande invalide");
            System.out.println("Commande valide : upload fichier /home/path/to/dowload/");
        } else {
            System.out.println("Telecharger " + commande[1] + " vers : " + commande[2]);
            String fichierDemande = commande[1];
                if(!commande[2].endsWith("/")){
                    System.out.println("Verifier le chemin de votre repoir (dois termier par '/')");
                    return;
                }
                String cheminFichier = commande[2] + fichierDemande;
                File fichierExistant = new File(cheminFichier);

                // Vérifier si le fichier existe déjà
                if (fichierExistant.exists()) {
                    System.out.println("Le fichier exite deja , Voulez-vous le remplacer ? oui/non");

                    String option = ecoute.nextLine();
                    if (option.equals("oui")) {

                    } else if (option.equals("non")) {
                        System.out.println("Telechargement annuler");
                        return;
                    } else {
                        System.out.println("Commande non valide");
                        return;
                    }
                }
                try {
                    dos.writeUTF("DOWNLOAD");
                    dos.writeUTF(fichierDemande);
                     
                    String reponse = dis.readUTF();
    
                    if ("OK".equals(reponse)) {
                        long tailleFichier = dis.readLong();
        
                        try (FileOutputStream fos = new FileOutputStream(cheminFichier)) {
                            byte[] buffer = new byte[8096];
                            int bytesRead;
                            long totalBytesRead = 0;
        
                            while ((bytesRead = dis.read(buffer)) != -1) {
                                fos.write(buffer, 0, bytesRead);
                                totalBytesRead += bytesRead;
        
                                if (totalBytesRead >= tailleFichier) {
                                    break;
                                }
                            }
                            System.out.println("Téléchargement terminé !");
                        //JOptionPane.showMessageDialog(this, "");
                        }
                    } else {
                        System.out.println("Fichier non trouvé sur le serveur");
                        //JOptionPane.showMessageDialog(this, ".");
                    }
                } catch (Exception e) {
                    // TODO: handle exception
                    System.out.println("Erreur : " + e.getMessage());
                }
               

        }
    }

    public static void deleteFile(String[] commande, DataInputStream dis, DataOutputStream dos) {
        try {
            if (commande.length != 2) {
                System.out.println("commande invalide");
                System.out.println("Commande valide : delete fichier");
            } else {
                System.out.println("Voulez vous confirmier la suppression de ce fichier ? oui/non");
                Scanner ecoute = new Scanner(System.in);
                String confirmation = ecoute.nextLine();
                confirmation = confirmation.trim();
                if (confirmation.equals("oui")) {
                    String nomFichier = commande[1];
                    dos.writeUTF("DELETE");
                    dos.writeUTF(nomFichier);
     
                    String reponse = dis.readUTF();

                    System.out.println(reponse);
                    
                } else if (confirmation.equals("non")) {
                    System.out.println("Suppresion annuler");
                } else {
                    System.out.println("Commande non valider , suppresion annuler");
                }
            }
        } catch (Exception e) {
            System.out.println("Erreur : " + e.getMessage());
        }

    }

    public static void uplaodFile(String[] commande, DataInputStream dis, DataOutputStream dos) {
        if (commande.length != 2) {
            System.out.println("commande invalide");
            System.out.println("Commande valide : dowload /home/path/fichier");
        } else {
            String filePath = commande[1];
            File file = new File(filePath);

            if (!file.exists() || !file.isFile()) {
                System.out.println("Le chemin fourni n'est pas un fichier valide.");
                return;
            } else {
                sendFile(file, dis, dos);
                System.out.println("Fichier valide trouvé : " + filePath);
            }
        }
    }

    public static void sendFile(File file, DataInputStream dis, DataOutputStream dos) {
        Scanner ecoute = new Scanner(System.in);
        try {
            dos.writeUTF(file.getName());

            dos.writeLong(file.length());

            // TELECHARMENT DU FICHIER
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = fis.read(buffer)) != -1) {
                    dos.write(buffer, 0, read);
                }
            }

            String uploadStatus = dis.readUTF();

            System.out.println(uploadStatus);
        } catch (IOException e) {
            System.out.println("Erreur lors de l'envoi du fichier : " + e.getMessage());
        }
    }

    public static void afficheListeFichier(DataInputStream dis, DataOutputStream dos) {
        
        try {
            dos.writeUTF("LIST");
             
            int fileCount = dis.readInt();
            System.out.println("Liste des fichiers : ");
            for (int i = 0; i < fileCount; i++) {
                System.out.println(dis.readUTF());
            }
            if(fileCount==0){
                System.out.println("Aucun fichier sur le serveur");
            }
            
        } catch (IOException e) {
            System.out.println("Erreur lors de la récupération de la liste des fichiers : " + e.getMessage());
        }
    }
}
