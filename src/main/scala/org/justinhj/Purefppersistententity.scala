package org.justinhj

import zio.{App, Queue, UIO, Ref}
import zio.console._
import zio.interop.cats._
import java.time.Instant
import cats.implicits._

trait PersistentEntity[ID, T <: PersistentEntity[ID, T]] {
  type Command
  type Event
  type State

  def id: ID
  def initialState: State

  def processCommand(command: Command): List[Event]
  def processEvent(event: Event, state: State): State

  def processEvents(events: List[Event], state: State): State = {
    events.foldLeft(state) {
      case (curState, event) =>
        processEvent(event, curState)
    }
  }

  // TODO scheduled timeout if no events

  private def commandLoop(state: Ref[State], queue: Queue[Command]) : UIO[Unit] = {
    for (
      command <- queue.take;
      _ = println(s"Received: $command");
      events = processCommand(command);
      // Save events... if saved events
      curState <- state.get;
      _ <- state.set(processEvents(events, curState));
      _ <- commandLoop(state, queue)
    ) yield ()
  }

  def start[R,E](): UIO[Queue[Command]] = {
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

sealed trait BankAccountCommand
case class DepositCmd(time: Instant, amount: Int)                       extends BankAccountCommand
case class PurchaseCmd(time: Instant, amount: Int)                      extends BankAccountCommand
case class AssignAccountHolderCmd(time: Instant, accountHolder: String) extends BankAccountCommand

sealed trait BankAccountEvent
case class DepositEvt(time: Instant, amount: Int)                       extends BankAccountEvent
case class PurchaseEvt(time: Instant, amount: Int)                      extends BankAccountEvent
case class AssignAccountHolderEvt(time: Instant, accountHolder: String) extends BankAccountEvent

case class AccountState(balance: Int, accountHolder: Option[String])

case class AccountEntity(id: Int, state: AccountState) extends PersistentEntity[Int, AccountEntity] {

  override type Command = BankAccountCommand
  override type Event   = BankAccountEvent
  override type State   = AccountState

  val initialState = AccountState(0, None)

  // Applies the command to the current state, returning a list of events
  def processCommand(command: Command): List[Event] =
    command match {
      case DepositCmd(time, amount) =>
        List(DepositEvt(time, amount))
      case PurchaseCmd(time, amount) =>
        if (amount <= state.balance)
          List(PurchaseEvt(time, amount))
        else
          List.empty
      case AssignAccountHolderCmd(time, accountHolder) =>
        List(AssignAccountHolderEvt(time, accountHolder))
    }

  // Processing an event applies changes to the current state
  // Since our entity is immutable we return a new one
  def processEvent(event: Event, state: State): State =
    event match {
      case DepositEvt(time, amount) =>
        AccountState(state.balance + amount, state.accountHolder)
      case PurchaseEvt(time, amount) =>
        AccountState(state.balance - amount, state.accountHolder)
      case AssignAccountHolderEvt(time, accountHolder) =>
        AccountState(state.balance, accountHolder.some)
    }
}

object Purefppersistententity extends App {

  val sampleAccount = AccountEntity(1, AccountState(0, None))
  val t1 = Instant.now

  val commands = List[BankAccountCommand](
    DepositCmd(t1.plusSeconds(10), 100),
    PurchaseCmd(t1.plusSeconds(20), 120),
    AssignAccountHolderCmd(t1.plusSeconds(40), "Bob Johnson"),
    DepositCmd(t1.plusSeconds(40), 100),
    AssignAccountHolderCmd(t1.plusSeconds(50), "Ben Johnson"),
    PurchaseCmd(t1.plusSeconds(60), 120)
  )

  def run(args: List[String]) =
    myAppLogic.fold(_ => 1, _ => 0)

  val myAppLogic = {
    for {
      eventQueue <- sampleAccount.start();
      _ <- putStrLn("Sending events...");
      command <- commands.map(eventQueue.offer(_)).sequence
    } yield ()
  }
}
