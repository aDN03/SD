package Servidor;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Servidor {
    private static final int MAX_SESSOES = 3; // Limite máximo de conexões
    private static int sessoesAtuais = 0; // Contador de sessões ativas
    private static final Lock lock = new ReentrantLock();
    private static final Condition podeConectar = lock.newCondition();

    public static void main(String[] args) {
        int porta = 12345; // Porta do servidor
        try (ServerSocket serverSocket = new ServerSocket(porta)) {
            System.out.println("Servidor aguardando conexões...");

            while (true) {
                Socket socket = serverSocket.accept();
                socket.setSoTimeout(30000);
                System.out.println("Cliente conectado: " + socket.getInetAddress());

                new Thread(() -> {
                    try {
                        gerenciarConexao(socket);
                    } catch (IOException e) {
                        System.out.println("Erro ao processar cliente: " + e.getMessage());
                    }
                }).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void gerenciarConexao(Socket socket) throws IOException {
        lock.lock();
        try {
            // Aguarda até que haja espaço para uma nova conexão
            while (sessoesAtuais >= MAX_SESSOES) {
                System.out.println("Cliente aguardando conexão: " + socket.getInetAddress());
                podeConectar.await(); // Bloqueia até ser notificado
            }

            // Incrementa o contador de sessões
            sessoesAtuais++;
            System.out.println("Sessão iniciada. Total de sessões ativas: " + sessoesAtuais);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return; // Sai se a thread for interrompida
        } finally {
            lock.unlock();
        }

        try {
            // Processa o cliente
            new ClienteHandler(socket).run();
        } finally {
            // Após finalizar, libera a sessão
            lock.lock();
            try {
                sessoesAtuais--;
                System.out.println("Sessão encerrada. Total de sessões ativas: " + sessoesAtuais);
                podeConectar.signal(); // Notifica uma thread aguardando
            } finally {
                lock.unlock();
            }
        }
    }
}
