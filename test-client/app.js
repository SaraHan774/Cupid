// 전역 변수
let currentUser = null;
let accessToken = null;
let refreshToken = null;
let stompClient = null;
let currentChannelId = null;
let usersCache = {}; // 사용자 정보 캐시 (senderId -> UserResponse)

// 페이지 로드 시 저장된 세션 복원
window.addEventListener('DOMContentLoaded', function() {
    restoreSession();
});

// API Base URL 설정
// config.js에서 환경에 따라 자동으로 설정됩니다
// 프로덕션/로컬 환경 전환은 config.js의 USE_PRODUCTION 변수를 변경하세요
const API_BASE = window.API_BASE || 'http://localhost:8080/api/v1';
const WS_BASE = window.WS_BASE || 'ws://localhost:8080/ws';  // WebSocket 엔드포인트 (/ws/chat이 아닌 /ws)

// 전역 스코프에 노출 (encryption-test.js에서 사용)
window.API_BASE = API_BASE;
window.WS_BASE = WS_BASE;

// 세션 저장
function saveSession() {
    if (currentUser && accessToken) {
        localStorage.setItem('currentUser', JSON.stringify(currentUser));
        localStorage.setItem('accessToken', accessToken);
        localStorage.setItem('refreshToken', refreshToken);
        console.log('세션 저장됨');
    }
}

// 세션 복원
function restoreSession() {
    const savedUser = localStorage.getItem('currentUser');
    const savedAccessToken = localStorage.getItem('accessToken');
    const savedRefreshToken = localStorage.getItem('refreshToken');

        if (savedUser && savedAccessToken) {
            try {
                currentUser = JSON.parse(savedUser);
                accessToken = savedAccessToken;
                refreshToken = savedRefreshToken;

                // 전역 스코프에 노출 (encryption-test.js에서 사용)
                window.accessToken = accessToken;

                console.log('세션 복원됨:', currentUser.username);

            // 로그인 화면 숨기고 메인 화면 표시
            document.getElementById('loginScreen').classList.add('hidden');
            document.getElementById('mainScreen').classList.remove('hidden');
            document.getElementById('currentUserDisplay').textContent = currentUser.username;
            document.getElementById('logoutBtn').classList.remove('hidden');

            // 사용자 ID 표시
            const myUserIdElement = document.getElementById('myUserId');
            if (myUserIdElement) {
                myUserIdElement.textContent = `User ID: ${currentUser.id}`;
                myUserIdElement.onclick = () => {
                    navigator.clipboard.writeText(currentUser.id);
                    alert('User ID가 클립보드에 복사되었습니다!');
                };
            }

            // WebSocket 연결
            connectWebSocket();

            // 채널 목록 로드
            loadChannels();
        } catch (error) {
            console.error('세션 복원 실패:', error);
            clearSession();
        }
    }
}

// 세션 삭제
function clearSession() {
    localStorage.removeItem('currentUser');
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    console.log('세션 삭제됨');
}

// 인증 탭 전환
function showAuthTab(tab) {
    document.querySelectorAll('.tab-btn').forEach(btn => btn.classList.remove('active'));
    document.querySelectorAll('.form').forEach(form => form.classList.remove('active'));
    
    if (tab === 'login') {
        // 로그인 탭 버튼 활성화
        const loginBtn = document.querySelector('.tab-btn');
        if (loginBtn) loginBtn.classList.add('active');
        document.getElementById('loginForm').classList.add('active');
    } else {
        // 회원가입 탭 버튼 활성화
        const registerBtn = Array.from(document.querySelectorAll('.tab-btn')).find(btn => btn.textContent.includes('회원가입'));
        if (registerBtn) registerBtn.classList.add('active');
        document.getElementById('registerForm').classList.add('active');
    }
}

// 메인 탭 전환
function showMainTab(tab) {
    document.querySelectorAll('.main-tab-btn').forEach(btn => btn.classList.remove('active'));
    document.querySelectorAll('.main-tab-content').forEach(content => content.classList.remove('active'));
    
    event.target.classList.add('active');
    
    if (tab === 'chat') {
        document.getElementById('chatTab').classList.add('active');
    } else if (tab === 'encryption') {
        document.getElementById('encryptionTab').classList.add('active');
    } else if (tab === 'api') {
        document.getElementById('apiTab').classList.add('active');
    }
}

// 회원가입
async function register() {
    const username = document.getElementById('registerUsername').value;
    const password = document.getElementById('registerPassword').value;
    const email = document.getElementById('registerEmail').value;

    try {
        const response = await fetch(`${API_BASE}/auth/register`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                username,
                password,
                email
            })
        });

        const data = await response.json();
        console.log('회원가입 응답:', data);

        if (data.success) {
            alert('회원가입 성공! 이제 로그인해주세요.');
            document.getElementById('registerError').textContent = '';
            // 로그인 탭으로 전환
            showAuthTab('login');
        } else {
            document.getElementById('registerError').textContent = data.error || data.message || '회원가입 실패';
        }
    } catch (error) {
        document.getElementById('registerError').textContent = '네트워크 오류';
        console.error('회원가입 오류:', error);
    }
}

// 로그인
async function login() {
    const username = document.getElementById('loginUsername').value;
    const password = document.getElementById('loginPassword').value;

    try {
        const response = await fetch(`${API_BASE}/auth/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password })
        });

        const data = await response.json();
        console.log('로그인 응답:', data);

        // 백엔드 응답 구조: { success, data: { user, accessToken, refreshToken }, message }
        if (data.success && data.data) {
            currentUser = data.data.user;
            accessToken = data.data.accessToken;
            refreshToken = data.data.refreshToken;

            // 전역 스코프에 노출 (encryption-test.js에서 사용)
            window.accessToken = accessToken;

            // 세션 저장
            saveSession();

            // 로그인 화면 숨기고 메인 화면 표시
            document.getElementById('loginScreen').classList.add('hidden');
            document.getElementById('mainScreen').classList.remove('hidden');
            document.getElementById('currentUserDisplay').textContent = currentUser.username;
            document.getElementById('logoutBtn').classList.remove('hidden');

            // 사용자 ID 표시
            const myUserIdElement = document.getElementById('myUserId');
            if (myUserIdElement) {
                myUserIdElement.textContent = `User ID: ${currentUser.id}`;
                myUserIdElement.onclick = () => {
                    navigator.clipboard.writeText(currentUser.id);
                    alert('User ID가 클립보드에 복사되었습니다!');
                };
            }

            // WebSocket 연결
            connectWebSocket();

            // 채널 목록 로드
            loadChannels();
        } else {
            document.getElementById('loginError').textContent = data.error || data.message || '로그인 실패';
        }
    } catch (error) {
        document.getElementById('loginError').textContent = '네트워크 오류';
        console.error('로그인 오류:', error);
    }
}

// 로그아웃
async function logout() {
    try {
        await fetch(`${API_BASE}/auth/logout`, {
            method: 'POST',
            headers: { 'Authorization': `Bearer ${accessToken}` }
        });

        // 연결 해제
        if (stompClient) {
            stompClient.disconnect();
            stompClient = null;
        }

        currentUser = null;
        accessToken = null;
        refreshToken = null;

        // 전역 스코프에서도 제거
        window.accessToken = null;

        // 세션 삭제
        clearSession();

        // 로그인 화면으로 돌아가기
        document.getElementById('mainScreen').classList.add('hidden');
        document.getElementById('loginScreen').classList.remove('hidden');
        document.getElementById('logoutBtn').classList.add('hidden');
        document.getElementById('currentUserDisplay').textContent = '';
    } catch (error) {
        console.error('로그아웃 오류:', error);
    }
}

// 채널 목록 로드
async function loadChannels() {
    const channelList = document.getElementById('channelList');
    channelList.innerHTML = '<p>채널을 불러오는 중...</p>';

    try {
        const response = await fetch(`${API_BASE}/chat/channels`, {
            headers: { 'Authorization': `Bearer ${accessToken}` }
        });

        const data = await response.json();
        console.log('채널 목록 응답:', data);

        // 백엔드 응답 구조: { success, channels: [...], total }
        if (data.success && data.channels && data.channels.length > 0) {
            channelList.innerHTML = '';
            data.channels.forEach(channel => {
                const item = document.createElement('div');
                item.className = 'channel-item';
                item.dataset.channelId = channel.id;
                item.innerHTML = `
                    <div class="channel-name">${channel.name || `${channel.type} 채널`}</div>
                    <div class="channel-type">${channel.type}</div>
                `;
                item.onclick = () => selectChannel(channel.id);
                channelList.appendChild(item);
            });
        } else {
            channelList.innerHTML = '<p>채널이 없습니다. 새 채널을 만들어보세요!</p>';
        }
    } catch (error) {
        channelList.innerHTML = '<p>채널 로드 실패</p>';
        console.error('채널 로드 오류:', error);
    }
}

// 채널 선택
async function selectChannel(channelId) {
    currentChannelId = channelId;

    // 활성 채널 표시
    document.querySelectorAll('.channel-item').forEach(item => {
        item.classList.toggle('active', item.dataset.channelId === channelId);
    });

    // 메시지 영역 표시
    document.getElementById('noChannelSelected').classList.add('hidden');
    document.getElementById('chatArea').classList.remove('hidden');

    // 채널별 WebSocket 구독
    subscribeToChannel(channelId);

    // 채널 정보 표시
    try {
        const response = await fetch(`${API_BASE}/chat/channels/${channelId}`, {
            headers: { 'Authorization': `Bearer ${accessToken}` }
        });

        const data = await response.json();
        console.log('채널 상세 응답:', data);

        if (data.success && data.channel) {
            const channel = data.channel;
            document.getElementById('currentChannelName').textContent = channel.name || `${channel.type} 채널`;
            document.getElementById('currentChannelType').textContent = channel.type;

            // 채널 멤버 표시 및 캐시에 저장
            const memberResponse = await fetch(`${API_BASE}/chat/channels/${channelId}/members`, {
                headers: { 'Authorization': `Bearer ${accessToken}` }
            });
            const memberData = await memberResponse.json();
            console.log('채널 멤버 응답:', memberData);

            if (memberData.success && memberData.members) {
                // 멤버 정보를 캐시에 저장
                memberData.members.forEach(member => {
                    usersCache[member.id] = member;
                });

                const memberNames = memberData.members.map(m => m.username).join(', ');
                document.getElementById('currentChannelType').textContent += ` (${memberNames})`;
            }
        }

        // 메시지 로드
        loadMessages();
    } catch (error) {
        console.error('채널 정보 로드 오류:', error);
    }
}

// 메시지 로드
async function loadMessages() {
    if (!currentChannelId) return;

    const messagesContainer = document.getElementById('messagesContainer');
    messagesContainer.innerHTML = '<p>메시지를 불러오는 중...</p>';

    try {
        const response = await fetch(`${API_BASE}/chat/channels/${currentChannelId}/messages?page=0&size=50`, {
            headers: { 'Authorization': `Bearer ${accessToken}` }
        });

        const data = await response.json();
        console.log('메시지 목록 응답:', data);

        // 백엔드 응답 구조: { success, messages: PagedResponse }
        if (data.success && data.messages) {
            const messages = data.messages.content || [];
            messagesContainer.innerHTML = '';
            if (messages.length > 0) {
                // 메시지를 역순으로 표시 (오래된 것부터)
                messages.reverse().forEach(msg => displayMessage(msg));
            } else {
                messagesContainer.innerHTML = '<p>메시지가 없습니다. 첫 메시지를 보내보세요!</p>';
            }
        } else {
            messagesContainer.innerHTML = '<p>메시지가 없습니다</p>';
        }
    } catch (error) {
        messagesContainer.innerHTML = '<p>메시지 로드 실패</p>';
        console.error('메시지 로드 오류:', error);
    }
}

// 메시지 표시
function displayMessage(msg) {
    const messagesContainer = document.getElementById('messagesContainer');
    const messageDiv = document.createElement('div');

    // 백엔드 응답에는 senderId만 있음
    const isSent = msg.senderId === currentUser.id;
    messageDiv.className = `message ${isSent ? 'sent' : ''}`;

    const date = new Date(msg.createdAt);

    // sender 정보 가져오기 (캐시 또는 senderId 표시)
    const senderName = getSenderName(msg.senderId);

    messageDiv.innerHTML = `
        <div class="message-header">
            <span class="message-sender">${senderName}</span>
            <span class="message-time">${date.toLocaleString('ko-KR', {
                month: 'short',
                day: 'numeric',
                hour: '2-digit',
                minute: '2-digit'
            })}</span>
        </div>
        <div class="message-content">${escapeHtml(msg.encryptedContent)}</div>
    `;

    messagesContainer.appendChild(messageDiv);
    messagesContainer.scrollTop = messagesContainer.scrollHeight;
}

// Sender 이름 가져오기
function getSenderName(senderId) {
    // 본인인 경우
    if (senderId === currentUser.id) {
        return currentUser.username;
    }

    // 캐시에서 찾기
    if (usersCache[senderId]) {
        return usersCache[senderId].username;
    }

    // 캐시에 없으면 비동기로 로드하고 임시 표시
    loadUserInfo(senderId);
    return '사용자';
}

// 사용자 정보 로드 및 캐싱
async function loadUserInfo(userId) {
    if (usersCache[userId]) return;

    try {
        // 현재 백엔드에는 특정 사용자 조회 API가 없으므로
        // 채널 멤버에서 가져오거나 senderId를 그대로 사용
        // 향후 GET /api/v1/users/{userId} API가 추가되면 사용
        usersCache[userId] = { id: userId, username: `사용자-${userId.substring(0, 8)}` };
    } catch (error) {
        console.error('사용자 정보 로드 실패:', error);
    }
}

// 메시지 전송
async function sendMessage() {
    if (!currentChannelId) return;

    const messageInput = document.getElementById('messageInput');
    const content = messageInput.value.trim();
    
    if (!content) return;

    // WebSocket이 연결되어 있으면 WebSocket으로 전송 (실시간)
    if (stompClient && stompClient.connected) {
        try {
            const messageData = {
                channelId: currentChannelId,
                encryptedContent: content,
                messageType: 'TEXT'
            };

            console.log('=== 메시지 전송 시작 ===');
            console.log('전송할 메시지:', messageData);
            console.log('현재 사용자 ID:', currentUser.id);

            stompClient.send(
                "/app/send",
                {},
                JSON.stringify(messageData)
            );

            // 입력창 비우기 (메시지는 WebSocket으로 받아서 표시됨)
            messageInput.value = '';
            console.log('WebSocket으로 메시지 전송 완료 - 브로드캐스트 대기 중');
        } catch (error) {
            console.error('WebSocket 전송 실패, HTTP로 재시도:', error);
            // WebSocket 실패 시 HTTP로 폴백
            sendMessageViaHttp(content);
        }
    } else {
        // WebSocket이 없으면 HTTP로 전송
        sendMessageViaHttp(content);
    }
}

// HTTP를 통한 메시지 전송 (폴백)
async function sendMessageViaHttp(content) {
    const messageInput = document.getElementById('messageInput');
    
    try {
        const response = await fetch(`${API_BASE}/chat/channels/${currentChannelId}/messages`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${accessToken}`
            },
            body: JSON.stringify({
                encryptedContent: content,
                messageType: 'TEXT'
            })
        });

        const data = await response.json();
        if (data.success) {
            messageInput.value = '';
            
            // HTTP로 전송된 메시지는 자동으로 표시되지 않으므로 수동 추가
            displayMessage(data.data);
        } else {
            alert(data.error || '메시지 전송 실패');
        }
    } catch (error) {
        alert('메시지 전송 오류');
        console.error(error);
    }
}

// WebSocket 연결
function connectWebSocket() {
    // 백엔드 WebSocket 엔드포인트: /ws (SockJS)
    // 쿼리 파라미터로 token 전달 (ConnectionInterceptor에서 처리)
    const socket = new SockJS(`${WS_BASE}?token=${accessToken}`);
    stompClient = Stomp.over(socket);

    // 디버그 로그 활성화
    stompClient.debug = (str) => {
        console.log('STOMP:', str);
    };

    stompClient.connect(
        { Authorization: `Bearer ${accessToken}` },
        function(frame) {
            console.log('WebSocket 연결 성공!', frame);

            // 메시지 수신 구독
            if (currentUser && currentUser.id) {
                const destination = `/user/${currentUser.id}/queue/messages`;
                console.log('=== 구독 시작 ===');
                console.log('구독 경로:', destination);
                console.log('현재 사용자 ID:', currentUser.id);

                const subscription = stompClient.subscribe(destination, function(message) {
                    console.log('=== 메시지 수신 콜백 실행됨! ===');
                    const data = JSON.parse(message.body);
                    console.log('받은 메시지:', data);
                    console.log('현재 채널 ID:', currentChannelId);
                    console.log('메시지 채널 ID:', data.channelId);

                    // 현재 선택된 채널의 메시지만 표시
                    if (data.channelId === currentChannelId) {
                        console.log('현재 채널의 메시지 - 화면에 표시합니다');
                        displayMessage(data);
                    } else {
                        console.log('다른 채널의 메시지 (알림 표시 가능):', data.channelId);
                        // TODO: 다른 채널에 새 메시지 뱃지 표시
                    }
                });

                console.log('구독 완료! subscription ID:', subscription.id);

                // 채널 초대 알림 구독
                const channelDestination = `/topic/user/${currentUser.id}/channels`;
                console.log('=== 채널 알림 구독 시작 ===');
                console.log('구독 경로:', channelDestination);

                stompClient.subscribe(channelDestination, function(message) {
                    console.log('=== 채널 알림 수신! ===');
                    const data = JSON.parse(message.body);
                    console.log('받은 알림:', data);

                    if (data.type === 'CHANNEL_INVITED') {
                        console.log('새 채널에 초대되었습니다:', data.channel);
                        // 채널 목록 새로고침 (먼저 실행)
                        loadChannels();
                        // 알림 표시 - setTimeout으로 비동기 처리하여 loadChannels가 먼저 완료되도록
                        setTimeout(() => {
                            alert(`새 채널에 초대되었습니다: ${data.channel.name || data.channel.type + ' 채널'}`);
                        }, 500);
                    }
                });

                console.log('채널 알림 구독 완료!');

            }
        },
        function(error) {
            console.error('WebSocket 연결 실패:', error);
            // 5초 후 재연결 시도
            setTimeout(() => {
                console.log('WebSocket 재연결 시도...');
                connectWebSocket();
            }, 5000);
        }
    );
}

// 채널별 WebSocket 구독 (채널 선택 시 호출)
let channelSubscription = null;

function subscribeToChannel(channelId) {
    // 기존 구독 해제
    if (channelSubscription) {
        channelSubscription.unsubscribe();
        console.log('기존 채널 구독 해제');
    }

    // 새 채널 구독
    if (stompClient && stompClient.connected && channelId) {
        const channelTopic = `/topic/channel/${channelId}`;
        console.log('=== 채널 구독 시작 ===');
        console.log('채널 ID:', channelId);
        console.log('구독 경로:', channelTopic);

        channelSubscription = stompClient.subscribe(channelTopic, function(message) {
            console.log('=== 채널 메시지 수신! ===');
            const data = JSON.parse(message.body);
            console.log('받은 메시지:', data);

            // 메시지 화면에 표시
            displayMessage(data);
        });

        console.log('채널 구독 완료!');
    }
}

// 채널 생성 다이얼로그 표시
function showCreateChannelDialog() {
    document.getElementById('createChannelDialog').classList.remove('hidden');
}

// 채널 생성 다이얼로그 닫기
function closeCreateChannelDialog() {
    document.getElementById('createChannelDialog').classList.add('hidden');
}

// 채널 생성
async function createChannel() {
    const name = document.getElementById('channelName').value;
    const type = document.getElementById('channelType').value;
    const inviteUserIdsInput = document.getElementById('inviteUserIds').value.trim();

    try {
        // 백엔드 CreateChannelRequest: { name?, type, matchId? }
        const requestBody = {
            name: name || null,
            type: type
        };

        console.log('채널 생성 요청:', requestBody);

        const response = await fetch(`${API_BASE}/chat/channels`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${accessToken}`
            },
            body: JSON.stringify(requestBody)
        });

        const data = await response.json();
        console.log('채널 생성 응답:', data);

        if (data.success) {
            const channelId = data.channel?.id;

            // 초대할 사용자 ID 목록 처리 (콤마로 구분)
            if (inviteUserIdsInput && channelId) {
                const userIds = inviteUserIdsInput
                    .split(',')
                    .map(id => id.trim())
                    .filter(id => id.length > 0);

                console.log('사용자 초대 시작:', userIds);

                let successCount = 0;
                let failCount = 0;

                for (const userId of userIds) {
                    try {
                        const inviteResponse = await fetch(`${API_BASE}/chat/channels/${channelId}/members`, {
                            method: 'POST',
                            headers: {
                                'Content-Type': 'application/json',
                                'Authorization': `Bearer ${accessToken}`
                            },
                            body: JSON.stringify({ userId: userId })
                        });

                        const inviteData = await inviteResponse.json();
                        console.log(`사용자 ${userId} 초대 응답:`, inviteData);

                        if (inviteData.success) {
                            successCount++;
                        } else {
                            failCount++;
                            console.warn(`사용자 ${userId} 초대 실패:`, inviteData.error);
                        }
                    } catch (inviteError) {
                        failCount++;
                        console.error(`사용자 ${userId} 초대 오류:`, inviteError);
                    }
                }

                if (failCount === 0) {
                    alert(`채널이 생성되었고, ${successCount}명의 사용자가 초대되었습니다!`);
                } else {
                    alert(`채널이 생성되었습니다.\n성공: ${successCount}명\n실패: ${failCount}명`);
                }
            } else {
                alert('채널이 생성되었습니다!');
            }

            closeCreateChannelDialog();
            // 채널 목록 새로고침
            await loadChannels();
            // 생성된 채널로 자동 이동
            if (channelId) {
                selectChannel(channelId);
            }
        } else {
            alert(data.error || data.message || '채널 생성 실패');
        }
    } catch (error) {
        alert('채널 생성 오류');
        console.error('채널 생성 오류:', error);
    }
}

// Enter 키로 메시지 전송
document.addEventListener('DOMContentLoaded', function() {
    const messageInput = document.getElementById('messageInput');
    if (messageInput) {
        messageInput.addEventListener('keypress', function(e) {
            if (e.key === 'Enter') {
                sendMessage();
            }
        });
    }
});

// HTML 이스케이프
function escapeHtml(text) {
    const map = {
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#039;'
    };
    return text.replace(/[&<>"']/g, m => map[m]);
}

