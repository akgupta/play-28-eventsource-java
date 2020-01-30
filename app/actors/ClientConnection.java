package actors;

import actors.ClientConnectionProtocol.ClientMessage;
import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Scheduler;
import akka.actor.Status;
import akka.actor.Terminated;
import java.util.concurrent.TimeUnit;
import play.libs.Json;
import scala.concurrent.duration.Duration;

import static play.libs.EventSource.Event.*;


public class ClientConnection extends AbstractActor {

    public static Props getProps(String connectionId, ActorRef eventSourceActor, Scheduler scheduler) {
        return Props.create(ClientConnection.class, () -> new ClientConnection(connectionId, eventSourceActor, scheduler));
    }

    private final String _connectionId;
    private final ActorRef _eventSourceActor;
    private Scheduler _scheduler;

    private ClientConnection(String connectionId, ActorRef eventSourceActor, Scheduler scheduler) {
        _connectionId = connectionId;
        _eventSourceActor = eventSourceActor;
        _scheduler = scheduler;

        // See https://doc.akka.io/docs/akka/2.5.4/java/stream/stream-integrations.html#source-actorref
        // The actor will be stopped when the stream is completed, failed or cancelled from downstream,
        // so we watch it to get notified when that happens and can shut down this client connection actor.
        getContext().watch(_eventSourceActor);
        // Respond on the event source with a connection established message
        _eventSourceActor.tell(event(Json.toJson(new ClientMessage(_connectionId, "connectionEstablished"))), getSelf());
    }

    @Override
    public void preStart() throws Exception {
        _scheduler.schedule(
            Duration.create(5, TimeUnit.SECONDS),
            Duration.create(5, TimeUnit.SECONDS),
            () -> _eventSourceActor.tell(event(Json.toJson(new ClientMessage(_connectionId, "heartbeat"))), getSelf()),
            getContext().getDispatcher()
        );

        _scheduler.scheduleOnce(
            Duration.create(31, TimeUnit.SECONDS),
            () -> _eventSourceActor.tell(new Status.Success("200"), getSelf()),
            getContext().dispatcher()
        );
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(
                Terminated.class,
                terminated -> {
                    if (terminated.getActor().equals(_eventSourceActor)) {
                        getContext().stop(self());
                    }
                }
            )
            .match(
                ClientMessage.class,
                clientMessage -> {
                    _eventSourceActor.tell(event(Json.toJson(clientMessage)), getSelf());
                }
            )
            .build();
    }
}
