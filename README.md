# WebRTC-sample

참고 했던 블로그들입니다.
-- 아래 링크로는 dependency 문제가 복잡해서 참고만 하였습니다.

```bash
https://proandroiddev.com/webrtc-sample-in-kotlin-e584681ed7fc
```

-- 현재 코드는 아래 링크분의 코드입니다.

```bash
https://aal-izz-well.tistory.com/entry/%EC%95%88%EB%93%9C%EB%A1%9C%EC%9D%B4%EB%93%9C-WebRTC-%ED%99%94%EC%83%81-%ED%86%B5%ED%99%94-%EA%B5%AC%ED%98%84-%EC%A7%84%ED%96%89-%EA%B3%BC%EC%A0%95-%EB%B0%8F-%EC%8B%9C%ED%96%89%EC%B0%A9%EC%98%A4-%EC%A0%95%EB%A6%AC
```


예제로 받은 siginal server는 현재 ec2 - ubuntu 인스턴스에서 실행중에 있습니다.


## INSTALL

### Signal Server

시그널 서버 디렉터리에서 진행.

```bash
cd SIGNAL_SERVER
```

노드 종속 패키지 설치.

```bash
npm install
```

Systemd 서비스 설치[^1].

```bash
sudo cp node-signal.service /usr/local/lib/systemd/system
sudo systemctl daemon-reload
```

서비스 실행

```bash
sudo systemctl enable --now node-signal
```

로그 확인

```bash
# 열람 모드
journalctl -u node-signal
# 실시간 모드
journalctl -u node-signal -f
```

[^1]: https://nodesource.com/blog/running-your-node-js-app-with-systemd-part-1/



음성 , 영상 데이터를 처럼 GPS 데이터를 webRTC를 통해 p2p 통신 가능하도록
클라이언트 code에 DataChannel을 추가했습니다.

-- 현재 앱에서 send Hello를 누르면 상대 피어에게 패킷이 전달됩니다.
```bash
참고자료 
 #-- 말씀해주신 SCTP가 떡하니 나왔습니다.. ㅋㅋ DataChannel이 SCTP를 사용한다고 하네요..
https://www.rfc-editor.org/rfc/rfc8831.html
https://andonekwon.tistory.com/65
https://itecnote.com/tecnote/java-working-with-datachannel-in-android-webrtc-application/
```