package Servidor;

import java.io.*;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.Arrays;

public class ClienteHandler implements Runnable {
    private final Socket socket;
    private static final long TIMEOUT = 120000;

    public ClienteHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        long start = System.currentTimeMillis();
        boolean autenticado = false;

        try (
                DataInputStream in = new DataInputStream(socket.getInputStream());
                DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
            // Autenticação (login ou registro)
            while (!autenticado && (System.currentTimeMillis() - start) < TIMEOUT) {
                try {
                    autenticado = autenticarCliente(in, out);
                } catch (Exception e) {
                    // Mantém a conexão para novas tentativas
                }
            }

            // Se chegou até aqui, o cliente está autenticado ou o tempo excedeu
            if (!autenticado) {
                System.out.println("Tempo excedido para autenticação.");
                return;
            }

            // Se chegou até aqui, o cliente está autenticado
            while (true) {
                processarComandos(in, out);
            }
        } catch (IOException e) {
            System.out.println("Encerrando conexão: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                System.out.println("Erro ao fechar o socket: " + e.getMessage());
            }
        }
    }

    private void processarComandos(DataInputStream in, DataOutputStream out) throws IOException {
        while (true) {
            String comando = in.readUTF();
            String comandoTipo = in.readUTF();

            if (comando.equals("1")) { // Escrita
                if (comandoTipo.equals("1")) { // Simples
                    String chave = in.readUTF(); // Lê a chave
                    int tamanho = in.readInt(); // Lê o tamanho do array de bytes
                    byte[] valor = new byte[tamanho];
                    in.readFully(valor); // Lê os bytes do cliente

                    // Lock para garantir a segurança do acesso ao armazenamento
                    Lock lock = ServidorArmazenamento.getLock();
                    lock.lock(); // Adquire o lock
                    try {
                        ServidorArmazenamento.getArmazenamento().put(chave, valor);
                        ServidorArmazenamento.getDataChangedCondition().signalAll();
                    } finally {
                        lock.unlock(); // Libera o lock
                    }

                    // out.writeUTF("Par chave-valor armazenado com sucesso.");
                    System.out.println("Escrita: [" + chave + "] = " + new String(valor));
                }

                else if (comandoTipo.equals("2")) { // Multipla
                    int repeticoes = in.readInt();

                    Lock lock = ServidorArmazenamento.getLock();
                    lock.lock(); // Adquire o lock
                    try {
                        for (int i = 0; i < repeticoes; i++) {
                            String chave = in.readUTF(); // Lê a chave
                            int tamanho = in.readInt(); // Lê o tamanho do array de bytes
                            byte[] valor = new byte[tamanho];
                            in.readFully(valor); // Lê os bytes do cliente

                            // Lock para garantir a segurança do acesso ao armazenamento
                            ServidorArmazenamento.getArmazenamento().put(chave, valor);

                            // out.writeUTF("Par chave-valor armazenado com sucesso.");
                            System.out.println("Escrita: [" + chave + "] = " + new String(valor));
                        }
                        ServidorArmazenamento.getDataChangedCondition().signalAll();
                    } finally {
                        lock.unlock(); // Libera o lock
                    }
                }
            }

            if (comando.equals("2")) { // Leitura
                if (comandoTipo.equals("1")) { // Simples
                    String chave = in.readUTF(); // Lê a chave           

                    // Lock para garantir segurança no acesso ao armazenamento
                    Lock lock = ServidorArmazenamento.getLock();
                    lock.lock(); // Adquire o lock
                    try {
                        byte[] valor = ServidorArmazenamento.getArmazenamento().get(chave);
                        if (valor == null) {
                            out.writeInt(0); // Envia tamanho 0 para indicar que a chave não existe
                            System.out.println("Leitura: Chave não encontrada - " + chave);
                        } else {
                            out.writeInt(valor.length); // Envia o tamanho do valor
                            out.write(valor); // Envia o valor como byte[]
                            System.out.println("Leitura: [" + chave + "] = " + new String(valor));
                        }
                    } finally {
                        lock.unlock();
                    }
                } else if (comandoTipo.equals("2")) { // Leitura composta
                    int repeticoes = in.readInt(); // Número de chaves fornecidas pelo cliente~
                    Map<String, byte[]> resultados = new HashMap<>(); // Armazena os pares chave-valor

                    Lock lock = ServidorArmazenamento.getLock();
                    lock.lock();

                    try {
                        for (int i = 0; i < repeticoes; i++) {
                            String chave = in.readUTF(); // Lê a chave enviada pelo cliente

                            // Lock para garantir acesso seguro ao armazenamento
                            byte[] valor = ServidorArmazenamento.getArmazenamento().get(chave); // Busca o valor
                            if (valor != null) {
                                resultados.put(chave, valor); // Adiciona ao mapa se a chave existe
                            } else {
                                System.out.println("Leitura: Chave não encontrada - " + chave);
                            }
                        }
                    } finally {
                        lock.unlock();
                    }

                    // Enviar os resultados ao cliente
                    out.writeInt(resultados.size()); // Envia o número de pares encontrados
                    for (Map.Entry<String, byte[]> entrada : resultados.entrySet()) {
                        out.writeUTF(entrada.getKey()); // Envia a chave
                        byte[] valor = entrada.getValue();
                        out.writeInt(valor.length); // Envia o tamanho do valor
                        out.write(valor); // Envia os bytes do valor
                    }
                } else if (comandoTipo.equals("3")) { // Leitura condicional
                    // Ler os parâmetros do cliente
                    String key = in.readUTF();
                    String keyCond = in.readUTF();
                    int condLength = in.readInt();
                    byte[] valueCond = new byte[condLength];
                    in.readFully(valueCond);

                    Lock lock = ServidorArmazenamento.getLock();
                    lock.lock();
                    try {
                        // Bloquear até que keyCond contenha valueCond
                        while (true) {
                            byte[] atual = ServidorArmazenamento.getArmazenamento().get(keyCond);
                            if (atual != null && Arrays.equals(atual, valueCond)) {
                                // Condição satisfeita
                                byte[] valorKey = ServidorArmazenamento.getArmazenamento().get(key);
                                if (valorKey == null) {
                                    out.writeInt(0);
                                } else {
                                    out.writeInt(valorKey.length);
                                    out.write(valorKey);
                                }
                                System.out.println("Leitura condicional: liberado para " + key);
                                break;
                            }
                            // Esperar sinal
                            ServidorArmazenamento.getDataChangedCondition().await();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        out.writeInt(0);
                    } finally {
                        lock.unlock();
                    }
                }
            }

            if (comando.equals("0")) {
                break;
            }
        }
    }

    private boolean autenticarCliente(DataInputStream in, DataOutputStream out) throws IOException {
        // Lê se o cliente quer 'login' ou 'register'
        String action = in.readUTF(); // "login" ou "register"
        String username = in.readUTF();
        String password = in.readUTF();

        if ("register".equalsIgnoreCase(action)) {
            boolean success = ServidorUsuarios.registerUser(username, password);
            if (success) {
                out.writeUTF("Registro bem-sucedido. Agora faça login.");
            } else {
                out.writeUTF("Falha no registro. Usuário já existe.");
            }
            return false;
        } else if ("login".equalsIgnoreCase(action)) {
            if (ServidorUsuarios.authenticate(username, password)) {
                out.writeUTF("Autenticação bem-sucedida. Bem-vindo!");
                System.out.println("Cliente autenticado: " + username);
                return true;
            } else {
                out.writeUTF("Autenticação falhou.");
                System.out.println("Falha na autenticação para: " + username);
                return false;
            }
        } else {
            out.writeUTF("Ação inválida.");
            return false;
        }
    }
}
