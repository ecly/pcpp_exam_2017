// COMPILE:
// javac -cp scala.jar:akka-actor.jar SecComSys.java 
// RUN:
// java -cp scala.jar:akka-actor.jar:akka-config.jar:. SecComSys

import java.util.*;
import java.io.*;
import akka.actor.*;

// -- HANDOUT --------------------------------------------------
class KeyPair implements Serializable {
    public final int public_key, private_key;
    public KeyPair(int public_key, int private_key) {
        this.public_key = public_key;
        this.private_key = private_key;
    }
}

class Crypto {
    static KeyPair keygen() {
        int public_key = (new Random()).nextInt(25)+1;
        int private_key = 26 - public_key;
        System.out.println("public key: " + public_key);
        System.out.println("private key: " + private_key);
        return new KeyPair(public_key, private_key);
    }

    static String encrypt(String cleartext, int key) {
        StringBuffer encrypted = new StringBuffer();
        for (int i=0; i<cleartext.length(); i++) {
            encrypted.append((char) ('A' + ((((int)
                                    cleartext.charAt(i)) - 'A' + key) % 26)));
        }
        return "" + encrypted;
    }
}

// -- MESSAGES --------------------------------------------------

class InitMessage implements Serializable {
    public final ActorRef R;
    public InitMessage(ActorRef R) {
        this.R = R;
    }
}

class RegisterMessage implements Serializable {
    public final ActorRef pid;
    public RegisterMessage(ActorRef pid) {
        this.pid = pid;
    }
}

class LookupMessage implements Serializable {
    public final ActorRef pid;
    public final ActorRef returnTo;
    public LookupMessage(ActorRef pid, ActorRef returnTo) {
        this.pid = pid;
        this.returnTo = returnTo;
    }
}

class KeyPairMessage implements Serializable {
    public final KeyPair keyPair;
    public KeyPairMessage(KeyPair keyPair) {
        this.keyPair = keyPair;
    }
}

class Message implements Serializable {
    public final String Y;
    public Message(String Y) {
        this.Y = Y;
    }
}

class CommMessage implements Serializable {
    public final ActorRef pid;
    public CommMessage(ActorRef pid) {
        this.pid = pid;
    }
}

class PubKeyMessage implements Serializable {
    public final ActorRef recipient;
    public final Integer publicKey;
    public PubKeyMessage(ActorRef recipient, int publicKey) {
        this.recipient = recipient;
        this.publicKey = publicKey;
    }
}

// -- ACTORS --------------------------------------------------

class RegistryActor extends UntypedActor {
    public final Map<ActorRef, Integer> registry = new HashMap<>();

    public void onReceive(Object o) throws Exception {
        if (o instanceof RegisterMessage) {
            RegisterMessage rm = (RegisterMessage) o;
            KeyPair keyPair = Crypto.keygen();
            registry.put(rm.pid, keyPair.public_key);
            rm.pid.tell(new KeyPairMessage(keyPair), getSelf());
        } else if (o instanceof LookupMessage) {
            LookupMessage lm = (LookupMessage) o;
            lm.returnTo.tell(new PubKeyMessage(lm.pid, registry.get(lm.pid)), getSelf());
        }
    }
}

class ReceiverActor extends UntypedActor {
    public ActorRef registry;
    public int publicKey;
    public int privateKey;

    public void onReceive(Object o) throws Exception {
        if (o instanceof InitMessage) {
            InitMessage im = (InitMessage) o;
            im.R.tell(new RegisterMessage(getSelf()), getSelf());
        } else if (o instanceof KeyPairMessage) {
            KeyPairMessage kpm = (KeyPairMessage) o;
            publicKey = kpm.keyPair.public_key;
            privateKey = kpm.keyPair.private_key;
        } else if (o instanceof Message) {
            Message m = (Message) o;
            String X = Crypto.encrypt(m.Y, privateKey);
            System.out.print("decrypted: '" + X + "'\n");
        }
    }
}

class SenderActor extends UntypedActor {
    public ActorRef registry;

    public void onReceive(Object o) throws Exception {
        if (o instanceof InitMessage) {
            InitMessage im = (InitMessage) o;
            registry = im.R;
        } else if (o instanceof CommMessage) {
            CommMessage cm = (CommMessage) o;
            registry.tell(new LookupMessage(cm.pid, getSelf()), getSelf());
        } else if (o instanceof PubKeyMessage) {
            PubKeyMessage pkm = (PubKeyMessage) o;
            String X = "SECRET";
            System.out.print("cleartext: '" + X + "'\n");
            String Y = Crypto.encrypt(X, pkm.publicKey);
            System.out.print("encypted: '" + Y + "'\n");
            pkm.recipient.tell(new Message(Y), getSelf());
        }
    }
}

// -- MAIN --------------------------------------------------

public class SecComSys {
    public static void main(String[] args) {
        final ActorSystem system = ActorSystem.create("SecComSys");
        final ActorRef registry = system.actorOf(Props.create(RegistryActor.class), "reigstry");
        final ActorRef receiver = system.actorOf(Props.create(ReceiverActor.class), "receiver");
        receiver.tell(new InitMessage(registry), ActorRef.noSender());
        final ActorRef sender = system.actorOf(Props.create(SenderActor.class), "sender");
        sender.tell(new InitMessage(registry), ActorRef.noSender());
        sender.tell(new CommMessage(receiver), ActorRef.noSender());
        system.shutdown();
    }
}
