package Cliente;

import java.io.*;
import java.net.*;

public class Cliente {
    public static void main(String[] args) {
        String host = "127.0.0.1";
        int porta = 12345;

        try (Socket socket = new Socket(host, porta)) {
            System.out.println("Conectado ao servidor.");

            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            BufferedReader input = new BufferedReader(new InputStreamReader(System.in));

            boolean autenticado = false;
            while (!autenticado) {
                System.out.println("Deseja fazer 'login' ou 'register'?");
                String type = input.readLine();
                out.writeUTF(type);

                System.out.println("Digite o nome de usuário:");
                String username = input.readLine();
                out.writeUTF(username);

                System.out.println("Digite a senha:");
                String password = input.readLine();
                out.writeUTF(password);

                String resposta = in.readUTF();
                System.out.println("Resposta do servidor: " + resposta);

                if (resposta.contains("Registro bem-sucedido")) {
                } else if (resposta.contains("Bem-vindo")) {
                    autenticado = true;
                } else {
                }
            }


            if (autenticado) {
                while (true) {
                    System.out.println("Escolha uma operação:");
                    System.out.println("1 - Nova operação");
                    System.out.println("0 - Sair");
                    String opcao = input.readLine();

                    if (opcao.equals("0")) {
                        out.writeUTF("0");
                        break;
                    } else if (opcao.equals("1")) {
                        System.out.println("Escolha o tipo de operação (1 - Escrita, 2 - Leitura): ");
                        String type = input.readLine();

                        String subtype = "";
                        if (type.equals("1")) {
                            System.out.println("1 - Escrita simples\n2 - Escrita múltipla");
                            subtype = input.readLine();
                        } else if (type.equals("2")) {
                            System.out.println("1 - Leitura simples\n2 - Leitura múltipla\n3 - Leitura condicional");
                            subtype = input.readLine();
                        }

                        Thread operation = new Thread(new OperationHandler(in, out, type, subtype, input));
                        operation.start();

                        try {
                            operation.join();
                        } catch (InterruptedException e) {
                            System.out.println("Thread de operação foi interrompida.");
                            Thread.currentThread().interrupt();
                        }

                    } else {
                        System.out.println("Opção inválida.");
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}