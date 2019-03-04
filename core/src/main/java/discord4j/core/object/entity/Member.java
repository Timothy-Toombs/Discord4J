/*
 * This file is part of Discord4J.
 *
 * Discord4J is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Discord4J is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Discord4J.  If not, see <http://www.gnu.org/licenses/>.
 */
package discord4j.core.object.entity;

import discord4j.core.ServiceMediator;
import discord4j.core.object.VoiceState;
import discord4j.core.object.data.stored.MemberBean;
import discord4j.core.object.data.stored.PresenceBean;
import discord4j.core.object.data.stored.UserBean;
import discord4j.core.object.data.stored.VoiceStateBean;
import discord4j.core.object.presence.Presence;
import discord4j.core.object.util.PermissionSet;
import discord4j.core.object.util.Snowflake;
import discord4j.core.spec.BanQuerySpec;
import discord4j.core.spec.GuildMemberEditSpec;
import discord4j.core.util.PermissionUtil;
import discord4j.store.api.util.LongLongTuple2;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.annotation.Nullable;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * A Discord guild member.
 *
 * @see <a href="https://discordapp.com/developers/docs/resources/guild#guild-member-object">Guild Member Object</a>
 */
public final class Member extends User {

    /** The raw data as represented by Discord. */
    private final MemberBean data;

    /** The ID of the guild this user is associated to. */
    private final long guildId;

    /**
     * Constructs a {@code Member} with an associated ServiceMediator and Discord data.
     *
     * @param serviceMediator The ServiceMediator associated to this object, must be non-null.
     * @param data The raw data as represented by Discord, must be non-null.
     * @param userData The user data as represented by Discord, must be non-null.
     * @param guildId The ID of the guild this user is associated to.
     */
    public Member(final ServiceMediator serviceMediator, final MemberBean data, final UserBean userData,
                  final long guildId) {
        super(serviceMediator, userData);
        this.data = Objects.requireNonNull(data);
        this.guildId = guildId;
    }

    @Override
    public Mono<Member> asMember(final Snowflake guildId) {
        return Mono.just(this);
    }

    /**
     * Gets the user's guild roles' IDs.
     *
     * @return The user's guild roles' IDs.
     */
    public Set<Snowflake> getRoleIds() {
        return Arrays.stream(data.getRoles())
                .mapToObj(Snowflake::of)
                .collect(Collectors.toSet());
    }

    /**
     * Requests to retrieve the user's guild roles.
     * <p>
     * The returned {@code Flux} will emit items in order based off their <i>natural</i> position, which is indicated
     * visually in the Discord client. For roles, the "lowest" role will be emitted first.
     *
     * @return A {@link Flux} that continually emits the user's guild {@link Role roles}. If an error is received, it is
     * emitted through the {@code Flux}.
     */
    public Flux<Role> getRoles() {
        return Flux.fromIterable(getRoleIds()).flatMap(id -> getClient().getRoleById(getGuildId(), id))
                .sort(Comparator.comparing(Role::getRawPosition).thenComparing(Role::getId));
    }

    /**
     * Gets when the user joined the guild.
     *
     * @return When the user joined the guild.
     */
    public Instant getJoinTime() {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(data.getJoinedAt(), Instant::from);
    }

    /**
     * Gets the ID of the guild this user is associated to.
     *
     * @return The ID of the guild this user is associated to.
     */
    public Snowflake getGuildId() {
        return Snowflake.of(guildId);
    }

    /**
     * Requests to retrieve the guild this user is associated to.
     *
     * @return A {@link Mono} where, upon successful completion, emits the {@link Guild guild} this user is associated
     * to. If an error is received, it is emitted through the {@code Mono}.
     */
    public Mono<Guild> getGuild() {
        return getClient().getGuildById(getGuildId());
    }

    /**
     * Gets the name that is displayed in client.
     *
     * @return The name that is displayed in client.
     */
    public String getDisplayName() {
        return getNickname().orElse(getUsername());
    }

    /**
     * Gets the user's guild nickname (if one is set).
     *
     * @return The user's guild nickname (if one is set).
     */
    public Optional<String> getNickname() {
        return Optional.ofNullable(data.getNick());
    }

    /**
     * Gets the <i>raw</i> nickname mention. This is the format utilized to directly mention another user (assuming the
     * user exists in context of the mention).
     *
     * @return The <i>raw</i> nickname mention.
     */
    public String getNicknameMention() {
        return "<@!" + getId().asString() + ">";
    }

    /**
     * Requests to retrieve this user's voice state for this guild.
     *
     * @return A {@link Mono} where, upon successful completion, emits a {@link VoiceState voice state} for this user
     * for this guild. If an error is received, it is emitted through the {@code Mono}.
     *
     * @implNote If the underlying {@link discord4j.core.DiscordClientBuilder#getStoreService() store} does not save
     * {@link VoiceStateBean} instances <b>OR</b> the bot is currently not logged in then the returned {@code Mono} will
     * always be empty.
     */
    public Mono<VoiceState> getVoiceState() {
        return getServiceMediator().getStateHolder().getVoiceStateStore()
                .find(LongLongTuple2.of(getGuildId().asLong(), getId().asLong()))
                .map(bean -> new VoiceState(getServiceMediator(), bean));
    }

    /**
     * Requests to retrieve the presence for this user for this guild.
     *
     * @return A {@link Mono} where, upon successful completion, emits a {@link Presence presence} for this user for
     * this guild. If an error is received, it is emitted through the {@code Mono}.
     *
     * @implNote If the underlying {@link discord4j.core.DiscordClientBuilder#getStoreService() store} does not save
     * {@link PresenceBean} instances <b>OR</b> the bot is currently not logged in then the returned {@code Mono} will
     * always be empty.
     */
    public Mono<Presence> getPresence() {
        return getServiceMediator().getStateHolder().getPresenceStore()
                .find(LongLongTuple2.of(getGuildId().asLong(), getId().asLong()))
                .map(Presence::new);
    }

    /**
     * Requests to kick this member.
     *
     * @return A {@link Mono} where, upon successful completion, emits nothing; indicating the member was kicked. If an
     * error is received, it is emitted through the {@code Mono}.
     */
    public Mono<Void> kick() {
        return kick(null);
    }

    /**
     * Requests to kick this member while optionally specifying the reason.
     *
     * @param reason The reason, if present.
     * @return A {@link Mono} where, upon successful completion, emits nothing; indicating the member was kicked. If an
     * error is received, it is emitted through the {@code Mono}.
     */
    public Mono<Void> kick(@Nullable final String reason) {
        return getServiceMediator().getRestClient().getGuildService()
                .removeGuildMember(getGuildId().asLong(), getId().asLong(), reason)
                .subscriberContext(ctx -> ctx.put("shard", getServiceMediator().getClientConfig().getShardIndex()));
    }

    /**
     * Requests to ban this user.
     *
     * @param spec A {@link Consumer} that provides a "blank" {@link BanQuerySpec} to be operated on.
     * @return A {@link Mono} where, upon successful completion, emits nothing; indicating this user was banned. If an
     * error is received, it is emitted through the {@code Mono}.
     */
    public Mono<Void> ban(final Consumer<? super BanQuerySpec> spec) {
        final BanQuerySpec mutatedSpec = new BanQuerySpec();
        spec.accept(mutatedSpec);

        return getServiceMediator().getRestClient().getGuildService()
                .createGuildBan(getGuildId().asLong(), getId().asLong(), mutatedSpec.asRequest(), mutatedSpec.getReason())
                .subscriberContext(ctx -> ctx.put("shard", getServiceMediator().getClientConfig().getShardIndex()));
    }

    /**
     * Requests to unban this user.
     *
     * @return A {@link Mono} where, upon successful completion, emits nothing; indicating this user was unbanned. If an
     * error is received, it is emitted through the {@code Mono}.
     */
    public Mono<Void> unban() {
        return unban(null);
    }

    /**
     * Requests to unban this user while optionally specifying the reason.
     *
     * @param reason The reason, if present.
     * @return A {@link Mono} where, upon successful completion, emits nothing; indicating this user was unbanned. If an
     * error is received, it is emitted through the {@code Mono}.
     */
    public Mono<Void> unban(@Nullable final String reason) {
        return getServiceMediator().getRestClient().getGuildService()
                .removeGuildBan(getGuildId().asLong(), getId().asLong(), reason)
                .subscriberContext(ctx -> ctx.put("shard", getServiceMediator().getClientConfig().getShardIndex()));
    }

    /**
     * Requests to add a role to this member.
     *
     * @param roleId The ID of the role to add to this member.
     * @return A {@link Mono} where, upon successful completion, emits nothing; indicating the role was added to this
     * member. If an error is received, it is emitted through the {@code Mono}.
     */
    public Mono<Void> addRole(final Snowflake roleId) {
        return addRole(roleId, null);
    }

    /**
     * Requests to add a role to this member while optionally specifying the reason.
     *
     * @param roleId The ID of the role to add to this member.
     * @param reason The reason, if present.
     *
     * @return A {@link Mono} where, upon successful completion, emits nothing; indicating the role was added to this
     * member. If an error is received, it is emitted through the {@code Mono}.
     */
    public Mono<Void> addRole(final Snowflake roleId, @Nullable final String reason) {
        return getServiceMediator().getRestClient().getGuildService()
                .addGuildMemberRole(guildId, getId().asLong(), roleId.asLong(), reason)
                .subscriberContext(ctx -> ctx.put("shard", getServiceMediator().getClientConfig().getShardIndex()));
    }

    /**
     * Requests to remove a role from this member.
     *
     * @param roleId The ID of the role to remove from this member.
     * @return A {@link Mono} where, upon successful completion, emits nothing; indicating the role was removed from
     * this member. If an error is received, it is emitted through the {@code Mono}.
     */
    public Mono<Void> removeRole(final Snowflake roleId) {
        return removeRole(roleId, null);
    }

    /**
     * Requests to remove a role from this member while optionally specifying the reason.
     *
     * @param roleId The ID of the role to remove from this member.
     * @param reason The reason, if present.
     *
     * @return A {@link Mono} where, upon successful completion, emits nothing; indicating the role was removed from
     * this member. If an error is received, it is emitted through the {@code Mono}.
     */
    public Mono<Void> removeRole(final Snowflake roleId, @Nullable final String reason) {
        return getServiceMediator().getRestClient().getGuildService()
                .removeGuildMemberRole(guildId, getId().asLong(), roleId.asLong(), reason)
                .subscriberContext(ctx -> ctx.put("shard", getServiceMediator().getClientConfig().getShardIndex()));
    }

    /**
     * Requests to calculate the permissions granted to this member by his roles in the guild.
     *
     * @return The permissions granted to this member by his roles in the guild.
     */
    public Mono<PermissionSet> getBasePermissions() {
        Mono<Boolean> getIsOwner = getGuild().map(guild -> guild.getOwnerId().equals(getId()));
        Mono<PermissionSet> getEveryonePerms = getGuild().flatMap(Guild::getEveryoneRole).map(Role::getPermissions);
        Mono<List<PermissionSet>> getRolePerms = getRoles().map(Role::getPermissions).collectList();

        return getIsOwner.filter(Predicate.isEqual(Boolean.TRUE))
                .flatMap(ignored -> Mono.just(PermissionSet.all()))
                .switchIfEmpty(Mono.zip(getEveryonePerms, getRolePerms, PermissionUtil::computeBasePermissions));
    }

    /**
     * Requests to determine if this member is higher in the role hierarchy than the provided member or signal
     * IllegalArgumentException if the provided member is in a different guild than this member.
     * This is determined by the positions of each of the members' highest roles.
     *
     * @param otherMember The member to compare in the role hierarchy with this member.
     * @return A {@link Mono} where, upon successful completion, emits {@code true} if this member is higher in the
     * role hierarchy than the provided member, {@code false} otherwise. If an error is received, it is emitted
     * through the {@code Mono}.
     */
    public Mono<Boolean> isHigher(Member otherMember) {
        if (!getGuildId().equals(otherMember.getGuildId())) {
            return Mono.error(new IllegalArgumentException("The provided member is in a different guild."));
        }

        // A member cannot be higher in the role hierarchy than himself
        if (this.equals(otherMember)) {
            return Mono.just(false);
        }

        // getRoles() emits items in order based off their natural position, the "highest" role, if present, will be
        // emitted last
        Mono<Integer> getThisHighestPosition = getRoles().flatMap(Role::getPosition).defaultIfEmpty(0).last();
        Mono<Integer> getOtherHighestPosition = otherMember.getRoles().flatMap(Role::getPosition).defaultIfEmpty(0).last();

        return getGuild().map(Guild::getOwnerId)
                .flatMap(ownerId -> {
                    if (ownerId.equals(getId())) {
                        return Mono.just(true);
                    }
                    if (ownerId.equals(otherMember.getId())) {
                        return Mono.just(false);
                    }
                    return Mono.zip(getThisHighestPosition, getOtherHighestPosition, (p1, p2) -> p1 > p2);
                });
    }

    /**
     * Requests to determine if this member is higher in the role hierarchy than the member as represented
     * by the supplied ID or signal IllegalArgumentException if the member as represented by the supplied ID is in
     * a different guild than this member.
     * This is determined by the positions of each of the members' highest roles.
     *
     * @param id The ID of the member to compare in the role hierarchy with this member.
     * @return A {@link Mono} where, upon successful completion, emits {@code true} if this member is higher in the role
     * hierarchy than the member as represented by the supplied ID, {@code false} otherwise. If an error is received,
     * it is emitted through the {@code Mono}.
     */
    public Mono<Boolean> isHigher(Snowflake id) {
        return getClient().getMemberById(getGuildId(), id).flatMap(this::isHigher);
    }

    /**
     * Requests to edit this member.
     *
     * @param spec A {@link Consumer} that provides a "blank" {@link GuildMemberEditSpec} to be operated on.
     * @return A {@link Mono} where, upon successful completion, emits nothing; indicating the member has been edited.
     * If an error is received, it is emitted through the {@code Mono}.
     */
    public Mono<Void> edit(final Consumer<? super GuildMemberEditSpec> spec) {
        final GuildMemberEditSpec mutatedSpec = new GuildMemberEditSpec();
        spec.accept(mutatedSpec);

        return getServiceMediator().getRestClient().getGuildService()
                .modifyGuildMember(getGuildId().asLong(), getId().asLong(), mutatedSpec.asRequest(), mutatedSpec.getReason())
                .subscriberContext(ctx -> ctx.put("shard", getServiceMediator().getClientConfig().getShardIndex()));
    }

    @Override
    public String toString() {
        return "Member{" +
                "data=" + data +
                ", guildId=" + guildId +
                "} " + super.toString();
    }
}
