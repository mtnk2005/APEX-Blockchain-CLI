/*
 * Copyright  2018 APEX Technologies.Co.Ltd. All rights reserved.
 *
 * FileName: Command.scala
 *
 * @author: ruixiao.xiao@chinapex.com: 18-8-10 下午1:55@version: 1.0
 */

package com.apex.cli.core

import com.apex.cli.RPC
import play.api.libs.json.Json

trait NewResult

case class NewError(e: Throwable) extends NewResult

case class NewSuccess(data: String) extends NewResult

case class NewInvalidParams(input: String) extends NewResult

case class NewUnKnown(cmd: String) extends NewResult

case class NewHelp(message: String) extends NewResult

case class NewNoInput() extends NewResult

case class NewQuit() extends NewResult

trait NewCommand {
  val cmd: String
  val description: String
  val paramList: NewParameterList = NewParameterList.empty
  val sys: Boolean = false

  def validate(params: List[String]): Boolean = {
    paramList.validate(params)
  }

  def execute(params: List[String]): NewResult = {
    try {
      NewSuccess(callRPC)
    } catch {
      case e: Throwable => NewError(e)
    }
  }

  protected def callRPC(): String = {
    val result = RPC.post(cmd, paramList.toJson)
    Json prettyPrint result
  }
}

object NewCommand {

  def execute(newCommand: String): NewResult = {
    if (!newCommand.trim.isEmpty) {
      val list = newCommand.trim.split("""\s+""").toList
      execCommand(list, all)
    } else {
      NewNoInput()
    }
  }

  def execCommand(list: List[String], all: Map[String, Seq[NewCommand]]): NewResult = {
    list match {
      case cmd :: tail if all.contains(cmd) =>
        all(cmd).find(_.validate(tail)) match {
          case Some(newCommand) =>
            newCommand.execute(tail)
          case None =>
            NewInvalidParams(tail.mkString(" "))
        }
      case cmd :: _ => NewUnKnown(cmd)
      case _ =>
        NewNoInput()
    }
  }

  def helpMessage(all: Map[String, Seq[NewCommand]], h: Boolean): String = {

    var message: String = null
    def paddingTail(str: String, padding: Int): String = {
      str.formatted(s"%-${padding}s")
    }

    if (message == null) {
      val title = "APEX NETWORK\n"

      var column= s"${paddingTail("name", 15)} description"
      if(h){
        column = s"${paddingTail("name", 15)} ${paddingTail("parameter", 30)} description"
      }

      val content = all.flatMap(
        p => p._2.filterNot(_.sys).map(c => {
          val cmd = if (c == p._2(0)) c.cmd else ""

          if(h){
            val params = s"[${c.paramList}]"
            s"${paddingTail(cmd, 15)} ${paddingTail(params, 30)} ${c.description}"
          }else{
            s"${paddingTail(cmd, 15)} ${c.description}"
          }

        })).mkString("\n")
      message = s"$title\n$column\n$content"
    }

    return message;
  }

  val all = Seq(
    new WalletCommand,
    new AccountCommand,
    new NewHelpC,
    new NewQuitC,
    new NewExitC
  ).groupBy(_.cmd)
}

trait NewCompositeCommand extends NewCommand {
  val subCommands: Seq[NewCommand]

  override def execute(params: List[String]): NewResult = {

    if(params.size == 0){

      NewHelp(helpMessage(false));
    }else if(params(0).startsWith("-")){

      checkHelpParam(params) match{
        case true => NewHelp(helpMessage(true))
        case _ => NewInvalidParams(params.mkString(" "))
      }

    }else{
      NewCommand.execCommand(params, subCommands.groupBy(_.cmd))
    }

  }

  private def helpMessage(h:Boolean): String ={
    val message = NewCommand.helpMessage(subCommands.groupBy(_.cmd), h)
    return message
  }

  private def checkHelpParam(params: List[String]):Boolean={
    if(params.size>1)false
    else if(!params(0).equals("-h")) false
    else true
  }
}


// ---------系统功能 start
class NewHelpC extends NewCommand {
  override val cmd: String = "help"
  override val description: String = "help"
  override val sys: Boolean = true

  override def execute(params: List[String]): NewResult = {

    val message = NewCommand.helpMessage(NewCommand.all, false)
    NewHelp(message)
  }
}

class NewQuitC extends NewCommand {
  override val cmd = "quit"
  override val description = "quit"
  override val sys: Boolean = true

  override def execute(params: List[String]): NewResult = new NewQuit
}

class NewExitC extends NewCommand {
  override val cmd = "exit"
  override val description = "exit"
  override val sys: Boolean = true

  override def execute(params: List[String]): NewResult = new NewQuit
}
// ---------系统功能 end

