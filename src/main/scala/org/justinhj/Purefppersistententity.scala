package org.justinhj

import zio.{App, Queue, UIO, Ref, ZIO}
import zio.console._
import zio.interop.catz._
import java.time.Instant
import cats.implicits._
import entities.PersistentEntity

// Bank account sample entity

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
    myAppLogic.map(_ => 0)

  /**
    * An Account entity is just the data and a persistent entity
    * PersistentEntity can be subscribed to
    * It can also be started
    * Once started it can be subscribed to
    *
    * You can use it directly but it is designed to be used through a manager
    *
    *
    */


  val myAppLogic = {
    val r = for {
      eventQueue <- sampleAccount.run();
      _ <- putStrLn("Sending events...");
      command <- commands.map{eventQueue.offer(_)}.sequence
    } yield ()

    r
  }
}
