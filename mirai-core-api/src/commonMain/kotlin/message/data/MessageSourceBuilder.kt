/*
 * Copyright 2019-2021 Mamoe Technologies and contributors.
 *
 *  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 *  Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 *  https://github.com/mamoe/mirai/blob/master/LICENSE
 */

@file:JvmMultifileClass
@file:JvmName("MessageUtils")
@file:Suppress("NOTHING_TO_INLINE", "unused", "INAPPLICABLE_JVM_NAME", "INVISIBLE_MEMBER")

package net.mamoe.mirai.message.data

import net.mamoe.mirai.Bot
import net.mamoe.mirai.IMirai
import net.mamoe.mirai.Mirai
import net.mamoe.mirai.contact.ContactOrBot
import net.mamoe.mirai.message.data.MessageSource.Key.quote
import net.mamoe.mirai.message.data.MessageSource.Key.recall
import net.mamoe.mirai.message.data.MessageSourceBuilder.Companion.create
import net.mamoe.mirai.utils.currentTimeSeconds

/**
 * 将在线消息源转换为离线消息源.
 */
@JvmName("toOfflineMessageSource")
public fun OnlineMessageSource.toOffline(): OfflineMessageSource =
    Mirai.constructMessageSource(botId, kind, fromId, targetId, ids, time, internalIds, originalMessage)

///////////////
//// AMEND ////
///////////////


/**
 * 复制这个消息源, 并以 [block] 修改
 *
 * @see buildMessageSource 查看更多说明
 */
@JvmName("copySource")
public fun MessageSource.copyAmend(
    block: MessageSourceAmender.() -> Unit
): OfflineMessageSource = MessageSourceAmender(this).apply(block).run {
    Mirai.constructMessageSource(botId, kind, fromId, targetId, ids, time, internalIds, originalMessage)
}

/**
 * 仅于 [copyAmend] 中修改 [MessageSource]
 */
public class MessageSourceAmender internal constructor(
    origin: MessageSource,
) : MessageSourceBuilder() {
    public var kind: MessageSourceKind = origin.kind
    public var originalMessage: MessageChain = origin.originalMessage

    public override var fromId: Long = origin.fromId
    public override var targetId: Long = origin.targetId
    public override var ids: IntArray = origin.ids
    public override var time: Int = origin.time
    public override var internalIds: IntArray = origin.internalIds

    /** 从另一个 [MessageSource] 中复制 [ids], [internalIds], [time]*/
    public fun metadataFrom(another: MessageSource) {
        this.ids = another.ids
        this.internalIds = another.internalIds
        this.time = another.time
    }
}


///////////////
//// BUILD ////
///////////////


/**
 * 构建一个 [OfflineMessageSource]
 *
 * ### 参数
 * 一个 [OfflineMessageSource] 需要以下参数:
 * - 发送人和发送目标: 通过 [MessageSourceBuilder.sender], [MessageSourceBuilder.target] 设置
 * - 消息元数据 (即 [MessageSource.ids], [MessageSource.internalIds], [MessageSource.time])
 *   元数据用于 [撤回][MessageSource.recall], [引用回复][MessageSource.quote], 和官方客户端定位原消息.
 *   可通过 [MessageSourceBuilder.ids], [MessageSourceBuilder.time], [MessageSourceBuilder.internalIds] 设置
 *   可通过 [MessageSourceBuilder.metadata] 从另一个 [MessageSource] 复制
 * - 消息内容: 通过 [MessageSourceBuilder.messages] 设置
 *
 * ### 性质
 * - 当两个消息的元数据相同时, 他们在群中会是同一条消息. 可通过此特性决定官方客户端 "定位原消息" 的目标
 * - 发送人的信息和消息内容会在官方客户端显示在引用回复中.
 *
 * ### 实例
 * ```
 * bot.buildMessageSource(MessageSourceKind.GROUP) {
 *     from(bot)
 *     target(target)
 *     metadata(source) // 从另一个消息源复制 ids, internalIds, time
 *
 *     messages { // 指定消息内容
 *         +"hi"
 *     }
 * }
 * ```
 *
 * @see copyAmend
 */
public fun IMirai.buildMessageSource(
    botId: Long,
    kind: MessageSourceKind,
    block: MessageSourceBuilder.() -> Unit
): OfflineMessageSource = MessageSourceBuilder.create().apply(block).run {
    Mirai.constructMessageSource(botId, kind, fromId, targetId, ids, time, internalIds, originalMessages.build())
}

/**
 * 构建一个 [OfflineMessageSource]
 *
 * @see buildMessageSource
 */
public fun Bot.buildMessageSource(
    kind: MessageSourceKind,
    block: MessageSourceBuilder.() -> Unit
): OfflineMessageSource = Mirai.buildMessageSource(this.id, kind, block)


/**
 * @see buildMessageSource
 * @see create
 */
public open class MessageSourceBuilder internal constructor() {
    public open var fromId: Long = 0
    public open var targetId: Long = 0

    public open var ids: IntArray = intArrayOf()

    /**
     * seconds
     * @see MessageSource.time
     */
    public open var time: Int = currentTimeSeconds().toInt()
    public open var internalIds: IntArray = intArrayOf()

    @PublishedApi
    internal val originalMessages: MessageChainBuilder = MessageChainBuilder()

    public fun time(from: MessageSource): MessageSourceBuilder = apply { this.time = from.time }
    public fun time(value: Int): MessageSourceBuilder = apply { this.time = value }

    public fun internalId(from: MessageSource): MessageSourceBuilder = apply { this.internalIds = from.internalIds }
    public fun internalId(vararg value: Int): MessageSourceBuilder = apply { this.internalIds = value }

    public fun id(from: MessageSource): MessageSourceBuilder = apply { this.ids = from.ids }
    public fun id(vararg value: Int): MessageSourceBuilder = apply { this.ids = value }


    /**
     * 从另一个 [MessageSource] 复制 [ids], [time], [internalIds].
     * 这三个数据决定官方客户端能 "定位" 到的原消息
     */
    public fun metadata(from: MessageSource): MessageSourceBuilder = apply {
        id(from)
        internalId(from)
        time(from)
    }

    /**
     * 从另一个 [MessageSource] 复制所有信息, 包括消息内容. 不会清空已有消息.
     */
    public fun allFrom(source: MessageSource): MessageSourceBuilder {
        this.ids = source.ids
        this.time = source.time
        this.fromId = source.fromId
        this.targetId = source.targetId
        this.internalIds = source.internalIds
        this.originalMessages.addAll(source.originalMessage)
        return this
    }


    /**
     * 从另一个 [MessageSource] 复制 [消息内容][MessageSource.originalMessage]. 不会清空已有消息.
     */
    public fun messagesFrom(source: MessageSource): MessageSourceBuilder = apply {
        this.originalMessages.addAll(source.originalMessage)
    }

    public fun messages(messages: Iterable<Message>): MessageSourceBuilder = apply {
        this.originalMessages.addAll(messages)
    }

    public fun messages(vararg message: Message): MessageSourceBuilder = apply {
        for (it in message) {
            this.originalMessages.add(it)
        }
    }

    @JvmSynthetic
    public inline fun messages(block: MessageChainBuilder.() -> Unit): MessageSourceBuilder = apply {
        this.originalMessages.apply(block)
    }

    public fun clearMessages(): MessageSourceBuilder = apply { this.originalMessages.clear() }

    /**
     * 设置发信人
     */
    public fun sender(sender: ContactOrBot): MessageSourceBuilder = apply {
        this.fromId = sender.id
    }

    /**
     * @see IMirai.getUin
     */
    public fun sender(uin: Long): MessageSourceBuilder = apply {
        this.fromId = uin
    }

    /**
     * 设置发信目标
     */
    public fun target(target: ContactOrBot): MessageSourceBuilder = apply {
        this.targetId = target.id
    }

    /**
     * @see IMirai.getUin
     */
    public fun target(uin: Long): MessageSourceBuilder = apply {
        this.targetId = uin
    }

    public fun setSenderAndTarget(sender: ContactOrBot, target: ContactOrBot): MessageSourceBuilder =
        sender(sender).target(target)

    public companion object {
        @JvmStatic
        public fun create(): MessageSourceBuilder = MessageSourceBuilder()
    }
}