import 'package:bling/core/client.dart';
import 'package:bling/core/models/group.dart';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

class ChatBanner extends StatefulWidget {
  final GroupModel group;
  final Function onPressed;
  ChatBanner(this.group, {required this.onPressed}) : super(key: ValueKey(group.groupUUID));

  @override
  _ChatBannerState createState() => _ChatBannerState();
}

class _ChatBannerState extends State<ChatBanner> {
  Widget? newMessageSign() {
    if(widget.group.messages.isNotEmpty && !widget.group.messages.last.seen){
      return Container(
        margin: EdgeInsets.only(right: 10.0),
        width: 10,
        height: 10,
        decoration: BoxDecoration(
            color: Colors.greenAccent,
            shape: BoxShape.circle
        ),
      );
    }
  }
  @override
  Widget build(BuildContext context) {
    String message = "";
    if(widget.group.messages.isNotEmpty){
      String sender = widget.group.messages.last.sender == Client.user.username || widget.group.messages.last.sender == ""
          ? "" : widget.group.messages.last.sender+": ";
      message = sender + widget.group.messages.last.message;
      if(message.length > 30){
        message = message.replaceRange(30, message.length, "...");
      }
    }
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: () => widget.onPressed(),
      child: Padding(
        padding: EdgeInsets.only(left: 15.0),
        child: Row(
          children: [
            Container(
              alignment: Alignment.center,
              padding: EdgeInsets.only(right: 12.0),
              child: CircleAvatar(
                    backgroundImage: NetworkImage("https://upload.wikimedia.org/wikipedia/commons/8/89/Portrait_Placeholder.png"), radius: 25.0),
            ),
            Expanded(
              child: Container(
                decoration: BoxDecoration(border: Border(bottom: BorderSide(color: Color(0xFFEEEEEE)))),
                height: 70,
                child: Padding(
                  padding: EdgeInsets.only(bottom: 35-7, top: 6.0),
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(widget.group.name, style: TextStyle(fontSize: 14)),
                      Row(children: [
                        Text(message, style: TextStyle(fontSize: 12)),
                        Padding(
                          padding: EdgeInsets.only(right: 10.0),
                          child: Text(widget.group.messages.isNotEmpty ? widget.group.messages.last.getFormattedDate() : "", style: TextStyle(
                            fontSize: 10
                          ),),
                        ),
                      ], mainAxisAlignment: MainAxisAlignment.spaceBetween,)

                    ],
                  ),
                )
              ),
            ),
            newMessageSign() ?? SizedBox()
          ],
        ),
      ),
    );
  }
}
