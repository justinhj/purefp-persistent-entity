package org.justinhj

import zio.{ App, IO, ZIO }
import zio.console._
import zio.clock._
import zio.actors.Actor.Stateful
import java.time.Instant
import zio.actors.{Context,ActorSystem,Supervisor}

object ZioActorsBankAccount extends App {

  case class AccountState(balance: Int, accountHolder: Option[String])

  sealed trait BankAccountCommand[+_]
  case class DepositCmd(time: Instant, amount: Int)                       extends BankAccountCommand[Unit]
  case class PurchaseCmd(time: Instant, amount: Int)                      extends BankAccountCommand[Unit]
  case class AssignAccountHolderCmd(time: Instant, accountHolder: String) extends BankAccountCommand[Unit]
  case object GetAccountCmd                                               extends BankAccountCommand[AccountState]

  def handleMessage[A](msg: BankAccountCommand[A], state: AccountState): IO[Nothing, (AccountState, A)] =
    msg match {
      case DepositCmd(time, amount) =>
        IO.effectTotal((AccountState(state.balance + amount, state.accountHolder), ()))
      case PurchaseCmd(time, amount) =>
        if (amount <= state.balance)
          IO.effectTotal((AccountState(state.balance - amount, state.accountHolder), ()))
        else
          IO.effectTotal((state, ()))
      case AssignAccountHolderCmd(time, accountHolder) =>
        IO.effectTotal((AccountState(state.balance, Some(accountHolder)), ()))
      case GetAccountCmd => IO.effectTotal((state, state))
    }

  // Messages
  val accountStateHandler = new Stateful[AccountState, Nothing, BankAccountCommand] {
    override def receive[A](
      state: AccountState,
      msg: BankAccountCommand[A],
      context: Context
    ): IO[Nothing, (AccountState, A)] =
      handleMessage(msg, state)
  }

  val myAppLogic: ZIO[Clock with Console, Throwable, Unit] = {
    val t1 = Instant.now()

    for {
      system <- ActorSystem("BankAccounts", remoteConfig = None);
      actor1 <- system.make("account1", Supervisor.none, AccountState(0, None), accountStateHandler);
      _ <- actor1 ! DepositCmd(t1.plusSeconds(10), 100);
      _ <- actor1 ! PurchaseCmd(t1.plusSeconds(20), 120);
      _ <- actor1 ! AssignAccountHolderCmd(t1.plusSeconds(40), "Bob Johnson");
      _ <- actor1 ! DepositCmd(t1.plusSeconds(40), 100);
      _ <- actor1 ! AssignAccountHolderCmd(t1.plusSeconds(50), "Ben Johnson");
      _ <- actor1 ! PurchaseCmd(t1.plusSeconds(60), 120);
      account <- actor1 ? GetAccountCmd;
      _ <- putStrLn(s"Account $account")
    } yield ()
  }

  def run(args: List[String]) =
    myAppLogic.fold(_ => 1, _ => 0)
}
