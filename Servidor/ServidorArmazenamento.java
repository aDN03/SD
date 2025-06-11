package Servidor;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;

public class ServidorArmazenamento {
    private static final Map<String, byte[]> armazenamento = new HashMap<>();
    private static final Lock lock = new ReentrantLock(); // Lock para sincronização
    private static final Condition dataChanged = lock.newCondition();

    public static Map<String, byte[]> getArmazenamento() {
        return armazenamento;
    }

    public static Lock getLock() {
        return lock;
    }

    public static Condition getDataChangedCondition(){
        return dataChanged;
    }
}
