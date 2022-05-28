////////////////////////////////////////////////////////////////////////////////
//  Copyright 2021 Cosgy Dev                                                   /
//                                                                             /
//     Licensed under the Apache License, Version 2.0 (the "License");         /
//     you may not use this file except in compliance with the License.        /
//     You may obtain a copy of the License at                                 /
//                                                                             /
//        http://www.apache.org/licenses/LICENSE-2.0                           /
//                                                                             /
//     Unless required by applicable law or agreed to in writing, software     /
//     distributed under the License is distributed on an "AS IS" BASIS,       /
//     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied./
//     See the License for the specific language governing permissions and     /
//     limitations under the License.                                          /
////////////////////////////////////////////////////////////////////////////////

package dev.cosgy.TextToSpeak;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.cosgy.TextToSpeak.audio.AudioHandler;
import dev.cosgy.TextToSpeak.audio.QueuedTrack;
import dev.cosgy.TextToSpeak.settings.Settings;
import dev.cosgy.TextToSpeak.utils.OtherUtil;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.ShutdownEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceJoinEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceLeaveEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceMoveEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceMuteEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageDeleteEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.slf4j.LoggerFactory.getLogger;

public class Listener extends ListenerAdapter {
    private final Bot bot;
    Logger log = getLogger(this.getClass());

    public Listener(Bot bot) {
        this.bot = bot;
    }

    @Override
    public void onReady(ReadyEvent event) {
        if (event.getJDA().getGuilds().isEmpty()) {
            Logger log = LoggerFactory.getLogger("YomiageBot");
            log.warn("このボットはグループに入っていません！ボットをあなたのグループに追加するには、以下のリンクを使用してください。");
            log.warn(event.getJDA().getInviteUrl(TextToSpeak.RECOMMENDED_PERMS));
        }
        if (bot.getConfig().useUpdateAlerts()) {
            bot.getThreadpool().scheduleWithFixedDelay(() ->
            {
                User owner = bot.getJDA().getUserById(bot.getConfig().getOwnerId());
                if (owner != null) {
                    String currentVersion = OtherUtil.getCurrentVersion();
                    String latestVersion = OtherUtil.getLatestVersion();
                    if (latestVersion != null && !currentVersion.equalsIgnoreCase(latestVersion) && TextToSpeak.CHECK_UPDATE) {
                        String msg = String.format(OtherUtil.NEW_VERSION_AVAILABLE, currentVersion, latestVersion);
                        owner.openPrivateChannel().queue(pc -> pc.sendMessage(msg).queue());
                    }
                }
            }, 0, 24, TimeUnit.HOURS);
        }
    }

    @Override
    public void onGuildMessageDelete(GuildMessageDeleteEvent event) {
    }

    @Override
    public void onGuildVoiceUpdate(@NotNull GuildVoiceUpdateEvent event) {
    }

    @Override
    public void onGuildVoiceLeave(@NotNull GuildVoiceLeaveEvent event) {
        Member botMember = event.getGuild().getSelfMember();

        Settings settings = bot.getSettingsManager().getSettings(event.getGuild());

        if (settings.isJoinAndLeaveRead() && Objects.requireNonNull(event.getGuild().getSelfMember().getVoiceState()).getChannel() == event.getChannelLeft() && event.getChannelLeft().getMembers().size() > 1) {
            String file = bot.getVoiceCreation().CreateVoice(event.getGuild(), event.getMember().getUser(), event.getMember().getUser().getName() + "が退出しました。");
            bot.getPlayerManager().loadItemOrdered(event.getGuild(), file, new Listener.LeaveResultHandler(null, event.getGuild(), event.getMember()));
        }

        if (event.getChannelLeft().getMembers().size() == 1 && event.getChannelLeft().getMembers().contains(botMember)) {
            AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
            handler.getQueue().clear();
            bot.getVoiceCreation().ClearGuildFolder(event.getGuild());
            if (bot.getConfig().getAutoEnterExit()) {
                event.getGuild().getAudioManager().closeAudioConnection();
            }
        }
    }

    @Override
    public void onGuildVoiceJoin(@NotNull GuildVoiceJoinEvent event) {
        Member botMember = event.getGuild().getSelfMember();
        Settings settings = bot.getSettingsManager().getSettings(event.getGuild());

        if (bot.getConfig().getAutoEnterExit()) {
            // 自分が入室した場合監視するチャンネルを更新する
            if (event.getMember() == botMember){
                bot.setWatchVoiceChannel(event.getChannelJoined());
                // botMember.modifyNickname(event.getChannelJoined().getName()+"担当");
            }

            // 監視しているチャンネルに入室した人が一人目の場合自分もそのチャンネルにJoinする
            if (event.getChannelJoined() == bot.getWatchVoiceChannel() && 
                    event.getChannelJoined().getMembers().size() == 1 &&
                    !event.getChannelJoined().getMembers().contains(botMember)) {
                event.getGuild().getAudioManager().openAudioConnection(event.getChannelJoined());
            }
        }

        if (settings.isJoinAndLeaveRead() && Objects.requireNonNull(event.getGuild().getSelfMember().getVoiceState()).getChannel() == event.getChannelJoined()) {
            String file = bot.getVoiceCreation().CreateVoice(event.getGuild(), event.getMember().getUser(), event.getMember().getUser().getName() + "が参加しました。");
            bot.getPlayerManager().loadItemOrdered(event.getGuild(), file, new Listener.JoinResultHandler(null, event.getGuild(), event.getMember(), false));
        }
    }

    @Override
    public void onGuildVoiceMove(@NotNull GuildVoiceMoveEvent event) {
        Member botMember = event.getGuild().getSelfMember();
        Settings settings = bot.getSettingsManager().getSettings(event.getGuild());

        // join
        if (bot.getConfig().getAutoEnterExit()) {
            // 自分が入室した場合監視するチャンネルを更新する
            if (event.getMember() == botMember){
                bot.setWatchVoiceChannel(event.getChannelJoined());
                // botMember.modifyNickname(event.getChannelJoined().getName()+"担当");
            }

            // 監視しているチャンネルに入室した人が一人目の場合自分もそのチャンネルにJoinする
            if (event.getChannelJoined() == bot.getWatchVoiceChannel() && 
                    event.getChannelJoined().getMembers().size() == 1 &&
                    !event.getChannelJoined().getMembers().contains(botMember)) {
                event.getGuild().getAudioManager().openAudioConnection(event.getChannelJoined());
            }
        }

        if (settings.isJoinAndLeaveRead() &&
            Objects.requireNonNull(event.getGuild().getSelfMember().getVoiceState()).getChannel() == event.getChannelJoined()) {
            String file = bot.getVoiceCreation().CreateVoice(event.getGuild(), event.getMember().getUser(), event.getMember().getUser().getName() + "が参加しました。");
            bot.getPlayerManager().loadItemOrdered(event.getGuild(), file, new Listener.JoinResultHandler(null, event.getGuild(), event.getMember(), false));
        }
        
        // leave
        if (settings.isJoinAndLeaveRead() &&
            Objects.requireNonNull(event.getGuild().getSelfMember().getVoiceState()).getChannel() == event.getChannelLeft() &&
            event.getChannelLeft().getMembers().size() > 1) {
                String file = bot.getVoiceCreation().CreateVoice(event.getGuild(), event.getMember().getUser(), event.getMember().getUser().getName() + "が移動しました。");
                bot.getPlayerManager().loadItemOrdered(event.getGuild(), file, new Listener.LeaveResultHandler(null, event.getGuild(), event.getMember()));
        }

        if (event.getChannelLeft().getMembers().size() == 1 && event.getChannelLeft().getMembers().contains(botMember)) {
            AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
            handler.getQueue().clear();
            bot.getVoiceCreation().ClearGuildFolder(event.getGuild());
            if (bot.getConfig().getAutoEnterExit()) {
                event.getGuild().getAudioManager().closeAudioConnection();
            }
        }
    }

    @Override
    public void onGuildVoiceMute(@NotNull GuildVoiceMuteEvent event) {
        Member botMember = event.getGuild().getSelfMember();
        VoiceChannel botChannel = botMember.getVoiceState().getChannel();
        VoiceChannel memberChannel = event.getVoiceState().getChannel();

        if (botChannel != null && memberChannel != null && botChannel == memberChannel) {
            String file;
            if (event.isMuted()) {
                file = bot.getVoiceCreation().CreateVoice(event.getGuild(), event.getMember().getUser(), event.getMember().getUser().getName() + "がミュートしました。");
            } else {
                file = bot.getVoiceCreation().CreateVoice(event.getGuild(), event.getMember().getUser(), event.getMember().getUser().getName() + "がミュートを解除しました。");
            }
            bot.getPlayerManager().loadItemOrdered(event.getGuild(), file, new Listener.JoinResultHandler(null, event.getGuild(), event.getMember(), false));
        }
    }

    @Override
    public void onShutdown(@NotNull ShutdownEvent event) {
        log.info("シャットダウンします。");
        bot.shutdown();
    }


    private class JoinResultHandler implements AudioLoadResultHandler {
        private final Message m;
        private final Guild guild;
        private final Member member;
        // private final GuildVoiceJoinEvent event;
        private final boolean ytsearch;

        private JoinResultHandler(Message m, Guild guild, Member member, boolean ytsearch) {
            this.m = m;
            this.guild = guild;
            this.member = member;
            this.ytsearch = ytsearch;
        }

        private void loadSingle(AudioTrack track, AudioPlaylist playlist) {
            AudioHandler handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
            handler.addTrack(new QueuedTrack(track, member.getUser()));
        }

        private int loadPlaylist(AudioPlaylist playlist, AudioTrack exclude) {
            int[] count = {0};
            playlist.getTracks().forEach((track) -> {
                if (!track.equals(exclude)) {
                    AudioHandler handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
                    handler.addTrack(new QueuedTrack(track, member.getUser()));
                    count[0]++;
                }
            });
            return count[0];
        }

        @Override
        public void trackLoaded(AudioTrack track) {
            loadSingle(track, null);
        }

        @Override
        public void playlistLoaded(AudioPlaylist playlist) {

        }

        @Override
        public void noMatches() {
        }

        @Override
        public void loadFailed(FriendlyException throwable) {

        }
    }

    private class LeaveResultHandler implements AudioLoadResultHandler {
        private final Message m;
        // private final GuildVoiceLeaveEvent event;
        private final Guild guild;
        private final Member member;

        private LeaveResultHandler(Message m, Guild guild, Member member) {
            this.m = m;
            // this.event = event;
            this.guild = guild;
            this.member = member;
        }

        private void loadSingle(AudioTrack track, AudioPlaylist playlist) {
            AudioHandler handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
            handler.addTrack(new QueuedTrack(track, member.getUser()));
        }

        @Override
        public void trackLoaded(AudioTrack track) {
            loadSingle(track, null);
        }

        @Override
        public void playlistLoaded(AudioPlaylist playlist) {

        }

        @Override
        public void noMatches() {
        }

        @Override
        public void loadFailed(FriendlyException throwable) {

        }
    }
}