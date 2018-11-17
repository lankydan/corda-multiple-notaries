package com.lankydanblog.tutorial.flows

import co.paralleluniverse.fibers.Suspendable
import com.lankydanblog.tutorial.contracts.MessageContract
import com.lankydanblog.tutorial.contracts.MessageContract.Commands.Send
import com.lankydanblog.tutorial.states.MessageState
import net.corda.core.contracts.Command
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

@InitiatingFlow
@StartableByRPC
@StartableByService
class SendMessageFlow(private val message: MessageState) :
  FlowLogic<SignedTransaction>() {

  @Suspendable
  override fun call(): SignedTransaction {
    logger.info("Started sending message ${message.contents}")
    val stx = collectSignature(verifyAndSign(transaction()))
    val tx = subFlow(FinalityFlow(stx))
    logger.info("Finished sending message ${message.contents}")
    return tx
  }

  @Suspendable
  private fun collectSignature(
    transaction: SignedTransaction
  ): SignedTransaction =
    subFlow(CollectSignaturesFlow(transaction, listOf(initiateFlow(message.recipient))))

  private fun verifyAndSign(transaction: TransactionBuilder): SignedTransaction {
    transaction.verify(serviceHub)
    return serviceHub.signInitialTransaction(transaction)
  }

  private fun transaction() =
    TransactionBuilder(notary()).apply {
      addOutputState(message, MessageContract.CONTRACT_ID)
      addCommand(Send(), message.participants.map(Party::owningKey))
    }

  private fun notary(): Party {
    val index = message.type.hashCode() % serviceHub.networkMapCache.notaryIdentities.size.also { logger.info("value of index: $it") }
    return serviceHub.networkMapCache.notaryIdentities.single { it.name.organisation == "Notary-$index" }
      .also { logger.info("Message: $message is being sent to Notary: $it") }
  }
}

@InitiatedBy(SendMessageFlow::class)
class SendMessageResponder(val session: FlowSession) : FlowLogic<Unit>() {

  @Suspendable
  override fun call() {
    subFlow(object : SignTransactionFlow(session) {
      override fun checkTransaction(stx: SignedTransaction) {}
    })
  }
}