package com.apex.cli

import com.apex.consensus.{RegisterData, VoteData, WitnessInfo}
import com.apex.core.{OperationType, Transaction, TransactionType}
import com.apex.crypto.Ecdsa.PublicKeyHash
import com.apex.crypto.{BinaryData, FixedNumber, UInt160}
import com.apex.vm.DataWord
import play.api.libs.json.{JsNull, Json}

/*
 * Copyright  2018 APEX Technologies.Co.Ltd. All rights reserved.
 *
 * FileName: .scala
 *
 * @author: whitney.wei@chinapex.com: 19-01-22 @version: 1.0
 */
class ProducerCommand extends CompositeCommand {
  override val cmd: String = "producer"
  override val description: String = "Operate producer"
  override val composite: Boolean = true

  private val registerNodeAddr = DataWord.of("0000000000000000000000000000000000000000000000000000000000000101")
  private val voteAddr = DataWord.of("0000000000000000000000000000000000000000000000000000000000000102")

  override val subCommands: Seq[Command] = Seq(
    new RegisterCommand,
    new ResisterCancelCommand,
    new VoteCommand,
    new VoteCancelCommand,
    new GetByAddrCommand,
    new ListCommand
  )

  private def buildTx(txType:TransactionType.Value, from:String, to:UInt160, data:Array[Byte]) = {

    val privKey = Account.getAccount(from).getPrivKey()

    val account = RPC.post("showaccount", s"""{"address":"${privKey.publicKey.address}"}""")

    var nextNonce: Long = 0
      if (account != JsNull) {
      nextNonce = (account \ "nextNonce").as[Long]
    }
    val tx = new Transaction(txType,
      privKey.publicKey.pubKeyHash,
      to,
      FixedNumber.Zero,
      nextNonce,
      data,
      FixedNumber.Zero,
      7000000L,
      BinaryData.empty)
    tx.sign(privKey)

    tx
  }

  private def sendTx(tx:Transaction) = {

    val txRawData = BinaryData(tx.toBytes)
    val rawTx: String = "{\"rawTx\":\"" + txRawData.toString + "\"}"
    val result = RPC.post("sendrawtransaction", rawTx)
    result
  }


  class RegisterCommand extends Command {
    override val cmd = "reg"
    override val description = "Register as an alternative production node"

    override val paramList: ParameterList = ParameterList.create(
      new NicknameParameter("from", "from", "The account where the asset come from. Omit it if you want to send your tokens to the default account in the active wallet.", true),
      new StringParameter("url", "url", "The node official website"),
      new StringParameter("country", "country", "Country where the node is located"),
      new StringParameter("address", "address", "Contact address"),
      new IntParameter("longitude", "longitude", "The longitude of the node"),
      new IntParameter("latitude", "latitude", "The latitude of the node")
    )

    override def execute(params: List[String]): Result = {
      try {
        val checkResult = Account.checkWalletStatus
        if (!checkResult.isEmpty) InvalidParams(checkResult)
        else {
          // 赋值from昵称
          var from = WalletCache.getActivityWallet().implyAccount
          // 根据昵称获取转账地址
          if (params.size / 2 == paramList.params.size) from = paramList.params(0).asInstanceOf[NicknameParameter].value

          if (!Account.checkAccountStatus(from)) InvalidParams("from account not exists, please type a different one")
          else {
            val fromHash = Account.getAccount(from).getPrivKey().publicKey.pubKeyHash
            val url = paramList.params(1).asInstanceOf[StringParameter].value
            val country = paramList.params(2).asInstanceOf[StringParameter].value
            val address = paramList.params(3).asInstanceOf[StringParameter].value
            val longitude = paramList.params(4).asInstanceOf[IntParameter].value
            val latitude = paramList.params(5).asInstanceOf[IntParameter].value

            val witnessInfo = new WitnessInfo(name = from, addr = fromHash, url = url, country = country, address = address, longitude = longitude, latitude = latitude);
            val registerData = new RegisterData(fromHash, witnessInfo, OperationType.register)
            val tx = buildTx(TransactionType.Call, from, registerNodeAddr.toUInt160, registerData.toBytes)
            val txResult = sendTx(tx)
            Success(Json prettyPrint txResult)
          }

        }
      } catch {
        case e: Throwable => Error(e)
      }
    }}

  class ResisterCancelCommand extends Command {
    override val cmd = "cancelReg"
    override val description = "Logout candidate/production node qualification"

    override val paramList: ParameterList = ParameterList.create(
      new NicknameParameter("from", "from", "The account where the asset come from. Omit it if you want to send your tokens to the default account in the active wallet.", true)
    )

    override def execute(params: List[String]): Result = {
      try {
        val checkResult = Account.checkWalletStatus
        if (!checkResult.isEmpty) InvalidParams(checkResult)
        else {
          // 赋值from昵称
          var from = WalletCache.getActivityWallet().implyAccount
          // 根据昵称获取转账地址
          if (params.size / 2 == paramList.params.size) from = paramList.params(0).asInstanceOf[NicknameParameter].value

          if (!Account.checkAccountStatus(from)) InvalidParams("from account not exists, please type a different one")
          else {
            val fromHash = Account.getAccount(from).getPrivKey().publicKey.pubKeyHash

            val witnessInfo = new WitnessInfo(name = from, addr = fromHash)
            val registerData = new RegisterData(fromHash, witnessInfo, OperationType.resisterCancel)

            val tx = buildTx(TransactionType.Call, from, registerNodeAddr.toUInt160, registerData.toBytes)
            val txResult = sendTx(tx)
            Success(Json prettyPrint txResult)
          }

        }
      } catch {
        case e: Throwable => Error(e)
      }
    }}

  class VoteCommand extends Command {
    override val cmd = "vote"
    override val description = "Vote for supported nodes"

    override val paramList: ParameterList = ParameterList.create(
      new NicknameParameter("from", "from", "The account where the asset come from. Omit it if you want to send your tokens to the default account in the active wallet.", true),
      new AddressParameter("address", "address", "The address of the voted node"),
      new AmountParameter("count", "count", "The number of votes")
    )

    override def execute(params: List[String]): Result = {
      try {
        val checkResult = Account.checkWalletStatus
        if (!checkResult.isEmpty) InvalidParams(checkResult)
        else {
          // 赋值from昵称
          var from = WalletCache.getActivityWallet().implyAccount
          // 根据昵称获取转账地址
          if (params.size / 2 == paramList.params.size) from = paramList.params(0).asInstanceOf[NicknameParameter].value

          if (!Account.checkAccountStatus(from)) InvalidParams("from account not exists, please type a different one")
          else {
            val fromHash = Account.getAccount(from).getPrivKey().publicKey.pubKeyHash
            val candidate = paramList.params(1).asInstanceOf[AddressParameter].value
            val count = paramList.params(2).asInstanceOf[AmountParameter].value

            val voteData = new VoteData(PublicKeyHash.fromAddress(candidate).get, FixedNumber.fromDecimal(count), OperationType.register)

            val tx = buildTx(TransactionType.Call, from, UInt160.fromBytes(voteAddr.data), voteData.toBytes)
            val txResult = sendTx(tx)
            Success(Json prettyPrint txResult)
          }
        }
      } catch {
        case e: Throwable => Error(e)
      }
    }
  }

  class VoteCancelCommand extends Command {
    override val cmd = "cancelVote"
    override val description = "Cancel vote"

    override val paramList: ParameterList = ParameterList.create(
      new NicknameParameter("from", "from", "The account where the asset come from. Omit it if you want to send your tokens to the default account in the active wallet.", true),
      new AddressParameter("address", "address", "The node address that canceled vote "),
      new AmountParameter("count", "count", "The number of votes canceled")
    )

    override def execute(params: List[String]): Result = {
      try {
        val checkResult = Account.checkWalletStatus
        if (!checkResult.isEmpty) InvalidParams(checkResult)
        else {
          // 赋值from昵称
          var from = WalletCache.getActivityWallet().implyAccount
          // 根据昵称获取转账地址
          if (params.size / 2 == paramList.params.size) from = paramList.params(0).asInstanceOf[NicknameParameter].value

          if (!Account.checkAccountStatus(from)) InvalidParams("from account not exists, please type a different one")
          else {
            val fromHash = Account.getAccount(from).getPrivKey().publicKey.pubKeyHash
            val candidate = paramList.params(1).asInstanceOf[AddressParameter].value
            val count = paramList.params(2).asInstanceOf[AmountParameter].value

            val voteData = new VoteData(PublicKeyHash.fromAddress(candidate).get, FixedNumber.fromDecimal(count), OperationType.register)

            val tx = buildTx(TransactionType.Call, from, UInt160.fromBytes(voteAddr.data), voteData.toBytes)
            val txResult = sendTx(tx)
            Success(Json prettyPrint txResult)
          }
        }
      } catch {
        case e: Throwable => Error(e)
      }
    }}

  class ListCommand extends Command {
    override val cmd = "list"
    override val description = "Query node information"

    override val paramList: ParameterList = ParameterList.create(
      new StringParameter("type", "type", "Query node information in different states")
    )

    override def execute(params: List[String]): Result = {
      try {
        val listType = paramList.params(0).asInstanceOf[StringParameter].value

        if(listType != "all" || listType != "active" || listType != "pending" || listType != "previous"){
          InvalidParams("type not exists, please type a different one")
        }

        val result = RPC.post("getProducers", s"""{"listType":"${listType}"}""")
        Success(Json prettyPrint result)
      } catch {
        case e: Throwable => Error(e)
      }
    }}

  class GetByAddrCommand extends Command {
    override val cmd = "getByAddr"
    override val description = "Query node information by node address"

    override val paramList: ParameterList = ParameterList.create(
      new StringParameter("address", "address", "The node address to be queried")
    )

    override def execute(params: List[String]): Result = {
      try {
        val address = paramList.params(0).asInstanceOf[StringParameter].value
        val result = RPC.post("getProducer", s"""{"address":"${address}"}""")
        Success(Json prettyPrint result)
      } catch {
        case e: Throwable => Error(e)
      }
    }}

}

