package Servidor;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ServidorUsuarios {
    private static final String USERS_FILE = "users.bin";
    private static final Map<String, String> users = new HashMap<>();
    private static final Lock userLock = new ReentrantLock();

    // Carrega usuários do arquivo
    static {
        loadUsers();
    }

    private static void loadUsers() {
        userLock.lock();
        try (DataInputStream in = new DataInputStream(new FileInputStream(USERS_FILE))) {
            while (true) {
                try {
                    int userLen = in.readInt();
                    if (userLen <= 0) break;
                    byte[] userBytes = new byte[userLen];
                    in.readFully(userBytes);
                    String user = new String(userBytes);

                    int passLen = in.readInt();
                    if (passLen <= 0) break;
                    byte[] passBytes = new byte[passLen];
                    in.readFully(passBytes);
                    String pass = new String(passBytes);

                    users.put(user, pass);
                } catch (EOFException e) {
                    break;
                }
            }
        } catch (FileNotFoundException e) {
            // Se o arquivo não existir ainda, sem problema
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            userLock.unlock();
        }
    }

    private static void saveUsers() {
        userLock.lock();
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(USERS_FILE))) {
            for (Map.Entry<String, String> entry : users.entrySet()) {
                byte[] userBytes = entry.getKey().getBytes();
                byte[] passBytes = entry.getValue().getBytes();

                out.writeInt(userBytes.length);
                out.write(userBytes);

                out.writeInt(passBytes.length);
                out.write(passBytes);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            userLock.unlock();
        }
    }

    public static boolean registerUser(String username, String password) {
        userLock.lock();
        try {
            if (users.containsKey(username)) {
                return false; // Já existe
            }
            users.put(username, password);
            saveUsers();
            return true;
        } finally {
            userLock.unlock();
        }
    }

    public static boolean authenticate(String username, String password) {
        userLock.lock();
        try {
            return password.equals(users.get(username));
        } finally {
            userLock.unlock();
        }
    }
}
