// COMPILE:
// javac -cp scala.jar:akka-actor.jar Broadcast.java 
// RUN:
// java -cp scala.jar:akka-actor.jar:akka-config.jar:. Broadcast

import java.util.*;
import java.io.*;
import akka.actor.*;

// -- MESSAGES --------------------------------------------------

class InitDispatcherMessage implements Serializable {
    public final ActorRef odd;
    public final ActorRef even;
    public final ActorRef collector;
    public InitDispatcherMessage(ActorRef odd, ActorRef even, ActorRef collector) {
        this.odd = odd;
        this.even = even;
        this.collector = collector;
    }
}

class InitWorkerMessage implements Serializable {
    public final ActorRef collector;
    public InitWorkerMessage(ActorRef collector) {
        this.collector = collector;
    }
}

class NumMessage implements Serializable {
    public final Integer num;
    public NumMessage(Integer num) {
        this.num = num;
    }
}

// -- ACTORS --------------------------------------------------
class DispatcherActor extends UntypedActor {
    ActorRef odd;
    ActorRef even;

    public void onReceive(Object o) throws Exception {
        if (o instanceof InitDispatcherMessage) {
            InitDispatcherMessage dm = (InitDispatcherMessage) o;
            odd = dm.odd;
            even = dm.even;
            odd.tell(new InitWorkerMessage(dm.collector), getSelf());
            even.tell(new InitWorkerMessage(dm.collector), getSelf());
        } else if (o instanceof NumMessage) {
            NumMessage nm = (NumMessage) o;
            if (nm.num % 2 == 0)
                even.tell(new NumMessage(nm.num), getSelf());
            else
                odd.tell(new NumMessage(nm.num), getSelf());
        }
    }
}

class WorkerActor extends UntypedActor {
    ActorRef collector;

    public void onReceive(Object o) throws Exception {
        if (o instanceof InitWorkerMessage) {
            InitWorkerMessage wm = (InitWorkerMessage) o;
            collector = wm.collector;
        } else if (o instanceof NumMessage) {
            NumMessage nm = (NumMessage) o;
            collector.tell(new NumMessage(nm.num*nm.num), getSelf());
        }
    }
}

class CollectorActor extends UntypedActor {
    public void onReceive(Object o) throws Exception {
        if (o instanceof NumMessage) {
            NumMessage nm = (NumMessage) o;
            System.out.println(nm.num);
        }
    }
}

// -- MAIN --------------------------------------------------

public class Broadcast {
    public static void main(String[] args) {
	final ActorSystem system = ActorSystem.create("OddEvenSystem");
	final ActorRef dispatcher = system.actorOf(Props.create(DispatcherActor.class), "dispatcher");
	final ActorRef collector = system.actorOf(Props.create(CollectorActor.class), "collector");
	final ActorRef odd = system.actorOf(Props.create(WorkerActor.class), "odd");
	final ActorRef even = system.actorOf(Props.create(WorkerActor.class), "even");
	dispatcher.tell(new InitDispatcherMessage(odd, even, collector), ActorRef.noSender());
    try {
        for(int i = 0; i <= 10; i++){
            dispatcher.tell(new NumMessage(i), ActorRef.noSender());
        }
	} catch(Exception e) {
	    e.printStackTrace();
	} finally {
	    system.shutdown();
	}
    }
}

