package org.justinhj.entities

import zio.{Queue, ZIO, Ref, URIO, UIO}
import zio.console.Console

// Manages persistent entities. Involves creating them (they will take care of event sourced init)
// You can become a client of the entity manager which allows you to receive events
// and send events from all entities of that type
case class PersistentEntityManager[EntityID, E <: PersistentEntity[EntityID, _]]() {

    val regQ = Queue.bounded[Int](128)
    val state = Ref.make(0)

    // Must run a manager
    // For now this will listen for register events from clients
    // and print them
    def run[R <: Console](): URIO[R, Unit] = {

        def commandLoop(queue: Queue[Int]) : UIO[Unit] = {
                // for (
                // command <- queue.take;
                // _ = println(s"Received: $command");
                // // Save events... if saved events
                // curState <- state.get;
                // _ <- state.set(curState + 1);
                // s <- state.get;
                // d <- ZIO.descriptor;
                // _ = println(s"State $s Fibre ${d.id}");
                // _ <- commandLoop(queue)
                // ) yield ()

            ???
        }

        val runLoop =
            for (
                state <- Ref.make(Map.empty[Int, Queue[Int]]);
                q <- regQ;
                _ <- commandLoop(q).forever.fork
            ) yield q
        runLoop.map(_ => ())
    }


//    def register()


}