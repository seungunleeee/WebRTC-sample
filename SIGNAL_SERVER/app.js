//소켓서버 세팅
const express = require("express");
const server = express();
const http = require("http").Server(server);
//클라이언트와 서버가 소켓 연결을 할 때 사용하는 소켓
const io = require("socket.io")(http);
const port = 5001;
//Room안에 있는 유저 객체
let users = {};
//Room을 담고있는 객체
let socketToRoom = {};
//Room안에 들어갈 수 있는 최대 인원 수
const maximum = 2;

server.get("/", function (req, res) {
  res.send("Hello World");
  console.log("serverGet");
  //소켓 연결 시 클라이언트에 전달해줄 html 문서
  // res.sendFile(__dirname + '/client.html');
});

//소켓 연결이 되면 실행되는 메서드
//서버와 클라이언트가 연결되면 소켓을 하나 생성한다.
io.on("connection", (socket) => {
  //연결성공
  console.log(`Socket connected(소켓 id) : ${socket.id}`);
  //방 입장
  socket.on("join_room", (data) => {
    console.log("data.roomName(방제목): " + data.roomName);
    //user[room]에는 room에 있는 사용자들이 배열 형태로 저장된다.
    //room이 존재하면
    if (users[data.roomName]) {
      //방에 입장한 사람의 수
      const length = users[data.roomName].length;
      //최대 인원을 충족시켰으면 더 이상 접속 불가
      if (length === maximum) {
        console.log("방 정원초과");
        //방 인원이 가득찬 경우
        socket.to(socket.id).emit("room_full");
        return;
      } else {
        console.log("방입장(배열저장)");
        //인원이 최대 인원보다 적으면 접속 가능
        //-data.roomName(방제목)으로 내가 속한 방(배열)을 찾는다.
        //내가 속한 방에 나의 socket.id를 저장한다.
        users[data.roomName].push({ id: socket.id });
      }
    } else {
      //room이 존재하지 않는다면 방 새로 생성 (배열 생성)
      //data.roomName: 방제목
      //socket.id: 유저의 소켓아이디
      console.log("방생성");
      users[data.roomName] = [{ id: socket.id }];
    }
    // 해당 소켓이 어느 room에 속해있는 지 알기 위해 저장
    //socket.id: 유저의 socket.id
    //data.roomName: 유저가 속한 방 제목
    socketToRoom[socket.id] = data.roomName;

    //방 입장
    //입력값으로 방제목(data.roomName)을 입력해서 유저를 방에 입장시킨다.
    socket.join(data.roomName);
    console.log(
      "socketToRoom(방제목), 참여자 Socket.id: " +
        `[${socketToRoom[socket.id]}]: ${socket.id}`
    );

    // 본인을 제외한 같은 room의 user array
    const usersInThisRoom = users[data.roomName].filter(
      (user) => user.id !== socket.id
    );
    //나를 제외했기 때문에 인원수가 2명이면 1명이 된다.
    console.log(
      "usersInThisRoom(본인제외 방 인원수): " + usersInThisRoom.length
    );
    // 본인에게 해당 방의 user array를 전송
    // 새로 접속하는 user가 이미 방에 있는 user들에게 offer(signal)를 보내기 위해
    // user array -> 본인을 제외한 본인이 속한 방의 유저 socket.id
    io.sockets.to(socket.id).emit("all_users", usersInThisRoom);
  });
  // 다른 user들에게 offer를 보냄 (자신의 RTCSessionDescription)
  socket.on("offer", (sdp) => {
    // room에는 두 명 밖에 없으므로 broadcast 사용해서 전달
    console.log("offer 내용 : " + sdp);
    socket.broadcast.emit("getOffer", sdp);
    console.log("offer: 상대방에게 offer 전달완료");
  });

  // offer를 보낸 user에게 answer을 보냄 (자신의 RTCSessionDescription)
  socket.on("answer", (sdp) => {
    // room에는 두 명 밖에 없으므로 broadcast 사용해서 전달
    // 여러 명 있는 처리는 다음 포스트 1:N에서...
    console.log("offer response 내용 : " + sdp);
    socket.broadcast.emit("getAnswer", sdp);
    console.log("answer: 상대방에게 answer 전달완료 ");
  });

  // 자신의 ICECandidate 정보를 signal(offer 또는 answer)을 주고 받은 상대에게 전달
  socket.on("candidate", (candidate) => {
    // room에는 두 명 밖에 없으므로 broadcast 사용해서 전달
    // 여러 명 있는 처리는 다음 포스트 1:N에서...
    console.log("candidate 내용 : " + candidate);
    socket.broadcast.emit("getCandidate", candidate);
    console.log("ICE후보 전달완료");
  });

  // user가 연결이 끊겼을 때 처리
  socket.on("disconnect", () => {
    console.log(`[${socketToRoom[socket.id]}]: ${socket.id} exit`);
    // disconnect한 user가 포함된 roomID
    const roomID = socketToRoom[socket.id];
    // room에 포함된 유저
    let room = users[roomID];
    // room이 존재한다면(user들이 포함된)
    if (room) {
      // disconnect user를 제외
      room = room.filter((user) => user.id !== socket.id);
      users[roomID] = room;
    }
    // 어떤 user가 나갔는 지 room의 다른 user들에게 통보
    socket.broadcast.to(room).emit("user_exit", { id: socket.id });
    console.log(users);
  });
});

//5001번 포트로 들어오는 접속 받기.
http.listen(port, function () {
  console.log("server on! port: " + port);
});
