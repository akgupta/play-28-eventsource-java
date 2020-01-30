package controllers;

import actors.ClientConnection;
import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.japi.Pair;
import akka.stream.Materializer;
import akka.stream.OverflowStrategy;
import akka.stream.javadsl.Source;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import javax.inject.Inject;
import javax.inject.Singleton;
import play.libs.EventSource;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import scala.concurrent.duration.Duration;

import static java.util.concurrent.TimeUnit.*;


/**
 * This controller contains an action to handle HTTP requests
 * to the application's home page.
 */
@Singleton
public class HomeController extends Controller {

    private final DateTimeFormatter df = DateTimeFormatter.ofPattern("HH mm ss");
    private ActorSystem _actorSystem;
    private Materializer _materializer;

    @Inject
    public HomeController(ActorSystem actorSystem, Materializer materializer) {
        _actorSystem = actorSystem;
        _materializer = materializer;
    }

    /**
     * An eventsource connection which sends down the current time to the client
     */
    public Result listen() {
        Source<String, ?> tickSource = Source.tick(Duration.Zero(), Duration.create(100, MILLISECONDS), "TICK");
        Source<EventSource.Event, ?> eventSource = tickSource.map(
            (tick) -> EventSource.Event.event(df.format(ZonedDateTime.now()))
        );
        return ok().chunked(eventSource.via(EventSource.flow())).as(Http.MimeTypes.EVENT_STREAM);
    }

    /**
     * An eventsource connection which is managed by a client connection actor.
     * This is an attempt to use the new Play 2.6 Eventsource API but not working as expected.
     */
    public Result connect() {
        // Create an actorRef based Source that produces EventSource Events whenever a message containing an
        // EventSource Event is sent to the actor.
        Source<EventSource.Event, ActorRef> matValuePoweredEventSource = Source.actorRef(100, OverflowStrategy.dropNew());

        // Source pre-materialization
        // https://doc.akka.io/docs/akka/current/stream/stream-flows-and-basics.html#source-pre-materialization
        Pair<ActorRef, Source<EventSource.Event, NotUsed>> actorRefEventSourcePair =
            matValuePoweredEventSource.preMaterialize(_materializer);
        ActorRef eventSourceActor = actorRefEventSourcePair.first();
        Source<EventSource.Event, ?> eventSource = actorRefEventSourcePair.second();

        // Create a client connection actor to manage the event source actor which represents the event source connection.
        String connectionIdentifier = UUID.randomUUID().toString();
        _actorSystem.actorOf(
            ClientConnection.getProps(connectionIdentifier, eventSourceActor, _actorSystem.getScheduler()),
            connectionIdentifier
        );

        // Connect event source to the incoming event source connection sink.
        return ok().chunked(eventSource.via(EventSource.flow())).as(Http.MimeTypes.EVENT_STREAM);
    }
}
