package org.justinhj.entities

import zio.{Queue, ZIO, Ref, UIO}

trait PersistentEntity[EntityID, T <: PersistentEntity[EntityID, T]] {
  type Command
  type Event
  type State
  type CommandReply

  def id: EntityID
  def initialState: State

  def processCommand(command: Command): List[Event]
  def processEvent(event: Event, state: State): State

  def processEvents(events: List[Event], state: State): State = {
    events.foldLeft(state) {
      case (curState, event) =>
        processEvent(event, curState)
    }
  }

  // Subscribe to responses
  def subscribe(queue: Queue[CommandReply]) : Int = ???

  // TODO scheduled timeout if no events

  private def commandLoop(state: Ref[State], queue: Queue[Command]) : UIO[Unit] = {
    for (
      command <- queue.take;
      _ = println(s"Received: $command");
      events = processCommand(command);
      // Save events... if saved events
      curState <- state.get;
      _ <- state.set(processEvents(events, curState));
      s <- state.get;
      d <- ZIO.descriptor;
      _ = println(s"State $s Fibre ${d.id}");
      _ <- commandLoop(state, queue)
    ) yield ()
  }

  def run[R,E](): UIO[Queue[Command]] = {
    for (
      state <- Ref.make(initialState);
      // TODO load events from storage
      // R must include persistence
      // E will be persistence layer errors that stop
      // this entity from continuing
      q <- Queue.bounded[Command](128);
      _ <- commandLoop(state, q).fork
    ) yield q
  }
}