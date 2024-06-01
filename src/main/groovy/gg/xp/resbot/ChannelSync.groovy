package gg.xp.resbot

import discord4j.discordjson.json.MessageData
import groovy.transform.CompileStatic
import groovy.transform.TupleConstructor
import org.apache.commons.lang3.StringUtils
import reactor.util.Logger
import reactor.util.Loggers

@CompileStatic
@TupleConstructor(includeFields = true, defaults = false)
class ChannelSync {
	private static final Logger log = Loggers.getLogger(ChannelSync)
	private final Bot bot
	private final ChannelController channel
	private final DesiredChannelContents desiredState


	SyncResult sync() {

		log.info "Beginning channel sync for ${channel.name}"
		ChannelOpsStats stats = new ChannelOpsStats()
		List<DesiredMarkdownMessage> desired = desiredState.desiredMessages()
		List<MessageData> actual = channel.getActualChannelContents()

		int desiredCount = desired.size()
		int actualCount = actual.size()

		// If count is the same, edit messages in-place
		// If desired > count, edit the first (count) messages, then add the rest
		// If desired < count, edit the first (desired) messages, then delete the rest
		int editCount = Math.min(desiredCount, actualCount)

		for (i in 0..<editCount) {

			def desiredMsg = desired[i]
			def actualMsg = actual[i]
			// Track the file for linking purposes
			if (desiredMsg instanceof FileBasedMarkdownMessage) {
				bot.setFileMapping(desiredMsg.file, actualMsg)
			}
			DesiredMessageContent desiredContent = desiredMsg.desiredContent

			if (desiredContent.pending()) {
				log.info("Pending edit: ${desiredMsg.title}")
				// If pending, DON'T edit. No reason to, when we're just going to edit it again
				stats.pending++
			}
			else if (!messageContentsEqual(actualMsg, desiredContent)) {
				log.info("Performing edit: ${desiredMsg.title}")
				MessageData afterEdit = channel.editMessage(actualMsg, desiredMsg)
				stats.edit++
				if (!messageContentsEqual(afterEdit, desiredContent)) {
					throw new RuntimeException("Message content was not as expected after edit. Expected:\n${desiredContent.content()}\n------\nActual:\n${afterEdit.content()}\n------\n")
				}
			}
			else {
				stats.noop++
			}
		}
		if (desiredCount > actualCount) {
			for (i in actualCount..<desiredCount) {
				def desiredMsg = desired[i]
				// We still post even if pending, because we can't mess up message order.
				// Anything pending will be fixed in the next pass.
				DesiredMessageContent desiredContent = desiredMsg.desiredContent
				if (desiredContent.pending()) {
					log.info("Create with pending links: ${desiredMsg.title}")
					stats.pending++
				}
				else {
					log.info("Create: ${desiredMsg.title}")
				}
				MessageData newMsg = channel.postMessage(desiredMsg)
				stats.create++
				if (desiredMsg instanceof FileBasedMarkdownMessage) {
					bot.setFileMapping(desiredMsg.file, newMsg)
				}
				if (!messageContentsEqual(newMsg, desiredContent)) {
					throw new RuntimeException("Message content was not as expected after create. Expected:\n${desiredContent.content()}\n------\nActual:\n${newMsg.content()}\n------\n")
				}
			}
		}
		else if (desiredCount < actualCount) {
			for (i in desiredCount..<actualCount) {
				def messageToDelete = actual[i]
				log.info("Delete message ${messageToDelete.id().asLong()} (${StringUtils.abbreviate(messageToDelete.content(), 50)})")
				channel.deleteMessage(messageToDelete)
				stats.delete++
			}
		}

		log.info "Channel ${channel.name} synced"
		log.info "${stats.format()}"
		log.info "Desired #: ${desiredCount}, Actual #: ${actualCount}"
		return new SyncResult(stats)
	}

	private static boolean messageContentsEqual(MessageData actualMessage, DesiredMessageContent desiredMessageContent) {
		return actualMessage.content() == desiredMessageContent.content()
	}
}
