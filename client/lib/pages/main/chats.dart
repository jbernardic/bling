import 'package:bling/config/routes.dart';
import 'package:bling/core/client.dart';
import 'package:bling/core/models/group.dart';
import 'package:bling/core/models/message.dart';
import 'package:bling/core/storage.dart';
import 'package:bling/widgets/chat_banner.dart';
import 'package:bling/widgets/loading_animation.dart';
import 'package:flutter/material.dart';

import '../../local_notifications.dart';
import '../chat.dart';

class ChatsPage extends StatefulWidget {
  static _ChatsPageState? of(BuildContext context) => context.findAncestorStateOfType();
  final Map<String, GroupModel> groups;
  ChatsPage(this.groups);
  @override
  _ChatsPageState createState() => _ChatsPageState();
}

class _ChatsPageState extends State<ChatsPage> {

  void seenMessages(GroupModel group){
    Storage.seenMessages(group);
    List<MessageModel> msgs = group.messages;
    for(int i = 0; i<msgs.length; i++){
      msgs[i].seen = true;
    }
  }

  void onMessage(data) async{
    var json = data[0];
    if(json == null) json = data;
    else data[1](Client.token); //send ack that message is delivered
    MessageModel message = MessageModel.fromJson(json);
    // fetch group if doesn't exist
    if(widget.groups[message.groupUUID] == null){
      Client.fetch("fetchGroup", args: [json["groupUUID"]], onData: (data){
        setState(() async{
          GroupModel group = GroupModel.fromJson(data);
          group.messages.add(message);
          widget.groups.putIfAbsent(data["groupUUID"], () => group);
          await Storage.addMessage(message);
        });
      });
      return;
    }
    GroupModel group = widget.groups[message.groupUUID]!;
    if(currentGroup != null){
      if(group.groupUUID == currentGroup?.groupUUID){
        message.seen = true;
      }
      //show local notification
      if(currentGroup?.groupUUID != message.groupUUID){
        GroupModel group = widget.groups[message.groupUUID]!;
        LocalNotifications.showMessageNotification(group.name, group.groupUUID, message.sender, message.message);
      }
    }
    setState(() {
      group.messages.add(message);
      //change group order
      widget.groups.remove(group.groupUUID);
      widget.groups.putIfAbsent(group.groupUUID, () => group);
    });
    await Storage.addMessage(message);
  }
  GroupModel? currentGroup;
  @override
  void initState() {
    super.initState();
    Client.socket.on("message", onMessage);
  }
  @override
  void dispose() {
    super.dispose();
    Client.socket.off("message", onMessage); //avoid memory leaks
  }
  void pushChat(GroupModel group){
    seenMessages(group);
    LocalNotifications.listeners.add(onNotification);
    currentGroup = group;
    Navigator.of(context).pushNamed("/chat", arguments: ChatArguments(group, onLeave: (){currentGroup = null;})).then((value) {
      setState(() {
        LocalNotifications.listeners.remove(onNotification);
      });
    });
  }
  void onNotification(String groupUUID){
    Navigator.of(context).pop();
    GroupModel group = Routes.groups[groupUUID]!;
    pushChat(group);
  }
  @override
  Widget build(BuildContext context) {
    return ListView(
      children: [
        Divider(),
        Column(
          children: [...widget.groups.values.map((e) => ChatBanner(e, onPressed: (){
            pushChat(e);
          }))].reversed.toList(),
        )
      ],
    );
  }
}