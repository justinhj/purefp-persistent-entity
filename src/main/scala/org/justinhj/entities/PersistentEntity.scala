package org.justinhj.entities

import zio.{Queue, ZIO, Ref, URIO}
import zio.console._

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

  private def commandLoop[R <: Console](state: Ref[State], queue: Queue[Command]) : URIO[R, Unit] = {
    for (
      command <- queue.take;
      _ <- putStrLn(s"Received: $command");
      events = processCommand(command);
      curState <- state.get;
      _ <- state.set(processEvents(events, curState));
      s <- state.get;
      fd <- ZIO.descriptor;
      _ <- putStr(s"State $s Fibre ${fd.id}");
      _ <- commandLoop(state, queue)
    ) yield ()
  }

  def run[R <: Console,E](): URIO[R, Queue[Command]] = {
    for (
      state <- Ref.make(initialState);
      q <- Queue.bounded[Command](128);
      _ <- commandLoop(state, q).fork
    ) yield q
  }
}