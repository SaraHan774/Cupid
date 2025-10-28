#!/usr/bin/env python3
"""
Cupid API 전체 엔드포인트 통합 테스트 스크립트

사용법:
    python test_all_endpoints.py

프로덕션 환경 테스트:
    python test_all_endpoints.py --base-url https://your-domain.com
"""

import json
import sys
import time
import uuid
import argparse
from typing import Dict, Optional, Any
import requests
from requests.exceptions import RequestException

class Colors:
    """ANSI 색상 코드"""
    GREEN = '\033[92m'
    RED = '\033[91m'
    YELLOW = '\033[93m'
    BLUE = '\033[94m'
    RESET = '\033[0m'
    BOLD = '\033[1m'

class ApiTester:
    def __init__(self, base_url: str = "http://localhost:8080", bypass_rate_limit: bool = True):
        self.base_url = base_url.rstrip('/')
        self.session = requests.Session()
        self.access_token: Optional[str] = None
        self.refresh_token: Optional[str] = None
        self.user_id: Optional[str] = None
        self.created_channel_id: Optional[str] = None
        self.created_message_id: Optional[str] = None
        self.fcm_token_id: Optional[str] = None
        self.test_username = f"test_user_{int(time.time())}"
        self.test_email = f"test_{int(time.time())}@example.com"
        self.bypass_rate_limit = bypass_rate_limit  # Rate limit 우회 플래그
        
        self.passed = 0
        self.failed = 0
        self.total_time = 0

    def print_header(self, text: str):
        """헤더 출력"""
        print(f"\n{Colors.BOLD}{Colors.BLUE}{'=' * 80}{Colors.RESET}")
        print(f"{Colors.BOLD}{Colors.BLUE}{text.center(80)}{Colors.RESET}")
        print(f"{Colors.BOLD}{Colors.BLUE}{'=' * 80}{Colors.RESET}\n")

    def print_test(self, method: str, endpoint: str):
        """테스트 시작 출력"""
        print(f"{Colors.BOLD}▶ {method.upper():<6} {endpoint}{Colors.RESET}")

    def print_success(self, message: str = ""):
        """성공 출력"""
        self.passed += 1
        print(f"  {Colors.GREEN}✓ PASS{Colors.RESET}", end="")
        if message:
            print(f" - {message}", end="")
        print()

    def print_failure(self, error: str):
        """실패 출력"""
        self.failed += 1
        print(f"  {Colors.RED}✗ FAIL{Colors.RESET} - {error}")

    def print_warning(self, message: str):
        """경고 출력"""
        print(f"  {Colors.YELLOW}⚠ WARN{Colors.RESET} - {message}")

    def print_info(self, message: str):
        """정보 출력"""
        print(f"  {Colors.BLUE}ℹ INFO{Colors.RESET} - {message}")

    def make_request(self, method: str, endpoint: str, data: Optional[Dict] = None, 
                     headers: Optional[Dict] = None, params: Optional[Dict] = None,
                     retry_on_rate_limit: bool = True) -> requests.Response:
        """HTTP 요청 실행"""
        url = f"{self.base_url}{endpoint}"
        request_headers = {
            "Content-Type": "application/json",
            "User-Agent": "test_all_endpoints.py/1.0",
            "X-Test-Script": "true"  # Rate Limit 우회 플래그
        }
        
        # 인증 토큰 추가
        if self.access_token and endpoint != "/api/v1/auth/login" and endpoint != "/api/v1/auth/register":
            request_headers["Authorization"] = f"Bearer {self.access_token}"
        
        if headers:
            request_headers.update(headers)
        
        start_time = time.time()
        try:
            if method.upper() == "GET":
                response = self.session.get(url, headers=request_headers, params=params)
            elif method.upper() == "POST":
                response = self.session.post(url, headers=request_headers, json=data)
            elif method.upper() == "PUT":
                response = self.session.put(url, headers=request_headers, json=data)
            elif method.upper() == "DELETE":
                response = self.session.delete(url, headers=request_headers)
            else:
                raise ValueError(f"지원하지 않는 HTTP 메서드: {method}")
            
            # Rate limit 체크 및 재시도
            if retry_on_rate_limit and response.status_code == 429:
                retry_after = response.headers.get("Retry-After", "1")
                try:
                    wait_time = int(retry_after)
                except (ValueError, TypeError):
                    wait_time = 2  # 기본값 2초
                
                self.print_warning(f"Rate limit 도달. {wait_time}초 후 재시도...")
                time.sleep(wait_time)
                
                # 재시도
                if method.upper() == "GET":
                    response = self.session.get(url, headers=request_headers, params=params)
                elif method.upper() == "POST":
                    response = self.session.post(url, headers=request_headers, json=data)
                elif method.upper() == "PUT":
                    response = self.session.put(url, headers=request_headers, json=data)
                elif method.upper() == "DELETE":
                    response = self.session.delete(url, headers=request_headers)
            
            elapsed = time.time() - start_time
            self.total_time += elapsed
            return response
        except RequestException as e:
            self.print_failure(f"요청 실패: {str(e)}")
            raise

    def test_health_check(self):
        """Health Check 테스트"""
        self.print_header("1. Health Check")
        self.print_test("GET", "/api/v1/health")
        
        try:
            response = self.make_request("GET", "/api/v1/health")
            if response.status_code == 200:
                data = response.json()
                self.print_success(f"Status: {data.get('status')}")
                if "services" in data:
                    services = data["services"]
                    for service, status in services.items():
                        status_text = status.get("status", "UNKNOWN")
                        color = Colors.GREEN if status_text == "UP" else Colors.RED
                        self.print_info(f"{service}: {color}{status_text}{Colors.RESET}")
            else:
                self.print_failure(f"Status code: {response.status_code}")
        except Exception as e:
            self.print_failure(str(e))

    def test_login_first(self):
        """먼저 기존 사용자로 로그인 시도"""
        self.print_header("2. 인증 - 로그인 (기존 사용자)")
        
        # 고정된 테스트 계정 정보
        login_data = {
            "username": "gaheemm",
            "password": "Android@12"
        }
        
        try:
            response = self.make_request("POST", "/api/v1/auth/login", data=login_data)
            if response.status_code == 200:
                data = response.json()
                if data.get("success") and "data" in data:
                    self.access_token = data["data"]["accessToken"]
                    self.refresh_token = data["data"]["refreshToken"]
                    self.test_username = "gaheemm"
                    self.test_email = data["data"]["user"]["email"]
                    self.user_id = data["data"]["user"]["id"]
                    self.print_success(f"기존 사용자로 로그인 성공 (User ID: {self.user_id})")
                    return True
                else:
                    self.print_warning("기존 사용자 로그인 실패")
                    return False
            else:
                self.print_warning("기존 사용자가 없음")
                return False
        except Exception as e:
            self.print_warning(f"로그인 시도 실패: {str(e)}")
            return False

    def test_register(self):
        """회원가입 테스트 (기존 사용자가 없을 때만)"""
        self.print_header("3. 인증 - 회원가입 (새 계정)")
        self.print_test("POST", "/api/v1/auth/register")
        
        register_data = {
            "username": self.test_username,
            "email": self.test_email,
            "password": "test123456"
        }
        
        try:
            response = self.make_request("POST", "/api/v1/auth/register", data=register_data)
            if response.status_code == 200:
                data = response.json()
                if data.get("success"):
                    self.print_success(f"User: {data['data']['username']}")
                    self.user_id = data['data']['id']
                else:
                    self.print_failure(data.get("message", "Unknown error"))
            else:
                self.print_failure(f"Status code: {response.status_code}")
        except Exception as e:
            self.print_failure(str(e))

    def test_login(self):
        """로그인 테스트"""
        self.print_header("4. 인증 - 로그인 (새 계정)")
        self.print_test("POST", "/api/v1/auth/login")
        
        login_data = {
            "username": self.test_username,
            "password": "test123456"
        }
        
        try:
            response = self.make_request("POST", "/api/v1/auth/login", data=login_data)
            if response.status_code == 200:
                data = response.json()
                if data.get("success") and "data" in data:
                    self.access_token = data["data"]["accessToken"]
                    self.refresh_token = data["data"]["refreshToken"]
                    self.print_success(f"Access Token 발급 완료 (User ID: {data['data']['user']['id']})")
                    self.print_info(f"Token expires in: {data['data']['expiresIn']}ms")
                else:
                    self.print_failure(data.get("message", "Unknown error"))
            else:
                self.print_failure(f"Status code: {response.status_code}")
        except Exception as e:
            self.print_failure(str(e))

    def test_validate_token(self):
        """토큰 검증 테스트"""
        self.print_header("5. 인증 - 토큰 검증")
        self.print_test("POST", "/api/v1/auth/validate")
        
        try:
            response = self.make_request("POST", "/api/v1/auth/validate")
            if response.status_code == 200:
                data = response.json()
                if data.get("success"):
                    self.print_success(f"Valid - User ID: {data['data']['userId']}")
                else:
                    self.print_failure(data.get("message", "Unknown error"))
            else:
                self.print_failure(f"Status code: {response.status_code}")
        except Exception as e:
            self.print_failure(str(e))

    def test_get_current_user(self):
        """현재 사용자 정보 조회 테스트"""
        self.print_header("6. 인증 - 현재 사용자 조회")
        self.print_test("GET", "/api/v1/auth/me")
        
        try:
            response = self.make_request("GET", "/api/v1/auth/me")
            if response.status_code == 200:
                data = response.json()
                if data.get("success"):
                    user = data["data"]
                    self.print_success(f"Username: {user['username']}, Email: {user['email']}")
                else:
                    self.print_failure(data.get("message", "Unknown error"))
            else:
                self.print_failure(f"Status code: {response.status_code}")
        except Exception as e:
            self.print_failure(str(e))

    def test_change_password(self):
        """비밀번호 변경 테스트"""
        self.print_header("7. 인증 - 비밀번호 변경")
        self.print_test("POST", "/api/v1/auth/change-password")
        
        # 기존 사용자 비밀번호 또는 새 계정 비밀번호
        current_password = "Android@12" if self.test_username == "gaheemm" else "test123456"
        change_password_data = {
            "currentPassword": current_password,
            "newPassword": "newtest123456"
        }
        
        try:
            response = self.make_request("POST", "/api/v1/auth/change-password", data=change_password_data)
            if response.status_code == 200:
                data = response.json()
                if data.get("success"):
                    self.print_success(data.get("message", "비밀번호 변경 완료"))
                else:
                    self.print_failure(data.get("message", "Unknown error"))
            else:
                self.print_failure(f"Status code: {response.status_code}")
        except Exception as e:
            self.print_failure(str(e))

    def test_ping(self):
        """핑 테스트 (접속 시간 업데이트)"""
        self.print_header("8. 인증 - Ping (접속 시간 업데이트)")
        self.print_test("POST", "/api/v1/auth/ping")
        
        try:
            response = self.make_request("POST", "/api/v1/auth/ping")
            if response.status_code == 200:
                data = response.json()
                if data.get("success"):
                    self.print_success(data.get("message", "Ping 성공"))
                else:
                    self.print_failure(data.get("message", "Unknown error"))
            else:
                self.print_failure(f"Status code: {response.status_code}")
        except Exception as e:
            self.print_failure(str(e))

    def test_update_profile(self):
        """프로필 업데이트 테스트"""
        self.print_header("9. 인증 - 프로필 업데이트")
        self.print_test("PUT", "/api/v1/auth/profile")
        
        update_data = {
            "bio": "테스트 사용자 프로필입니다.",
            "profileImageUrl": "https://example.com/profile.jpg"
        }
        
        try:
            response = self.make_request("PUT", "/api/v1/auth/profile", data=update_data)
            if response.status_code == 200:
                data = response.json()
                if data.get("success"):
                    self.print_success(data.get("message", "프로필 업데이트 완료"))
                else:
                    self.print_failure(data.get("message", "Unknown error"))
            else:
                self.print_failure(f"Status code: {response.status_code}")
        except Exception as e:
            self.print_failure(str(e))

    def test_register_fcm_token(self):
        """FCM 토큰 등록 테스트"""
        self.print_header("10. 알림 - FCM 토큰 등록")
        self.print_test("POST", "/api/v1/notifications/fcm-token")
        
        fcm_data = {
            "token": f"test_fcm_token_{int(time.time())}",
            "deviceType": "IOS",
            "deviceName": "iPhone 14",
            "appVersion": "1.0.0"
        }
        
        try:
            response = self.make_request("POST", "/api/v1/notifications/fcm-token", data=fcm_data)
            if response.status_code == 200:
                data = response.json()
                if data.get("success"):
                    self.print_success(data.get("message", "FCM 토큰 등록 완료"))
                    # token_id 저장 (추후 삭제용)
                    self.fcm_token_id = "temp_token_id"
                else:
                    self.print_failure(data.get("message", "Unknown error"))
            else:
                self.print_failure(f"Status code: {response.status_code}")
        except Exception as e:
            self.print_failure(str(e))

    def test_get_fcm_tokens(self):
        """FCM 토큰 목록 조회 테스트"""
        self.print_header("11. 알림 - FCM 토큰 목록 조회")
        self.print_test("GET", "/api/v1/notifications/fcm-token")
        
        try:
            response = self.make_request("GET", "/api/v1/notifications/fcm-token")
            if response.status_code == 200:
                data = response.json()
                if data.get("success"):
                    count = data.get("count", 0)
                    self.print_success(f"등록된 토큰 개수: {count}")
                else:
                    self.print_failure(data.get("message", "Unknown error"))
            else:
                self.print_failure(f"Status code: {response.status_code}")
        except Exception as e:
            self.print_failure(str(e))

    def test_get_notification_settings(self):
        """알림 설정 조회 테스트"""
        self.print_header("12. 알림 - 설정 조회")
        self.print_test("GET", "/api/v1/notifications/settings")
        
        try:
            response = self.make_request("GET", "/api/v1/notifications/settings")
            if response.status_code == 200:
                data = response.json()
                if data.get("success"):
                    settings = data.get("settings", {})
                    self.print_success(f"Enabled: {settings.get('enabled', 'N/A')}")
                else:
                    self.print_failure(data.get("message", "Unknown error"))
            else:
                self.print_failure(f"Status code: {response.status_code}")
        except Exception as e:
            self.print_failure(str(e))

    def test_update_notification_settings(self):
        """알림 설정 업데이트 테스트"""
        self.print_header("13. 알림 - 설정 업데이트")
        self.print_test("PUT", "/api/v1/notifications/settings")
        
        settings_data = {
            "enabled": True,
            "soundEnabled": True,
            "vibrationEnabled": True,
            "showPreview": True
        }
        
        try:
            response = self.make_request("PUT", "/api/v1/notifications/settings", data=settings_data)
            if response.status_code == 200:
                data = response.json()
                if data.get("success"):
                    self.print_success(data.get("message", "설정 업데이트 완료"))
                else:
                    self.print_failure(data.get("message", "Unknown error"))
            else:
                self.print_failure(f"Status code: {response.status_code}")
        except Exception as e:
            self.print_failure(str(e))

    def test_get_online_users(self):
        """온라인 사용자 목록 조회 테스트"""
        self.print_header("14. 온라인 상태 - 사용자 목록 조회")
        self.print_test("GET", "/api/v1/online-status/users")
        
        try:
            response = self.make_request("GET", "/api/v1/online-status/users")
            if response.status_code == 200:
                data = response.json()
                count = data.get("totalOnlineUsers", 0)
                self.print_success(f"온라인 사용자: {count}명")
            else:
                self.print_failure(f"Status code: {response.status_code}")
        except Exception as e:
            self.print_failure(str(e))

    def test_get_online_stats(self):
        """온라인 상태 통계 조회 테스트"""
        self.print_header("15. 온라인 상태 - 통계 조회")
        self.print_test("GET", "/api/v1/online-status/stats")
        
        try:
            response = self.make_request("GET", "/api/v1/online-status/stats")
            if response.status_code == 200:
                data = response.json()
                count = data.get("totalOnlineUsers", 0)
                self.print_success(f"총 온라인 사용자: {count}명")
            else:
                self.print_failure(f"Status code: {response.status_code}")
        except Exception as e:
            self.print_failure(str(e))

    def test_get_channels(self):
        """채널 목록 조회 테스트"""
        self.print_header("16. 채널 - 목록 조회")
        self.print_test("GET", "/api/v1/channels")
        
        try:
            response = self.make_request("GET", "/api/v1/channels", params={"page": 0, "size": 10})
            if response.status_code == 200:
                data = response.json()
                if data.get("success"):
                    channels = data.get("channels", [])
                    self.print_success(f"채널 개수: {len(channels)}")
                else:
                    self.print_failure(data.get("message", "Unknown error"))
            else:
                self.print_failure(f"Status code: {response.status_code}")
        except Exception as e:
            self.print_failure(str(e))

    def test_create_channel(self):
        """채널 생성 테스트"""
        self.print_header("17. 채널 - 생성")
        self.print_test("POST", "/api/v1/channels")
        
        # 다른 사용자 생성이 필요하므로 1:1 채널 대신 그룹 채널 생성
        channel_data = {
            "type": "GROUP",
            "name": "테스트 그룹 채널",
            "description": "API 테스트용 채널"
        }
        
        try:
            response = self.make_request("POST", "/api/v1/channels", data=channel_data)
            if response.status_code == 201:
                data = response.json()
                if data.get("success"):
                    self.created_channel_id = data["channel"]["id"]
                    self.print_success(f"Channel ID: {self.created_channel_id}")
                else:
                    self.print_failure(data.get("message", "Unknown error"))
            else:
                self.print_failure(f"Status code: {response.status_code}")
        except Exception as e:
            self.print_failure(str(e))

    def test_get_channel_detail(self):
        """채널 상세 조회 테스트"""
        if not self.created_channel_id:
            self.print_warning("채널 ID가 없어 건너뜀")
            return
            
        self.print_header("18. 채널 - 상세 조회")
        self.print_test("GET", f"/api/v1/channels/{self.created_channel_id}")
        
        try:
            response = self.make_request("GET", f"/api/v1/channels/{self.created_channel_id}")
            if response.status_code == 200:
                data = response.json()
                if data.get("success"):
                    channel = data["channel"]
                    self.print_success(f"Name: {channel.get('name')}")
                else:
                    self.print_failure(data.get("message", "Unknown error"))
            else:
                self.print_failure(f"Status code: {response.status_code}")
        except Exception as e:
            self.print_failure(str(e))

    def test_send_message(self):
        """메시지 전송 테스트"""
        if not self.created_channel_id:
            self.print_warning("채널 ID가 없어 건너뜀")
            return
            
        self.print_header("19. 메시지 - 전송")
        self.print_test("POST", f"/api/v1/channels/{self.created_channel_id}/messages")
        
        message_data = {
            "encryptedContent": "테스트 메시지 내용입니다.",
            "messageType": "TEXT"
        }
        
        try:
            response = self.make_request("POST", f"/api/v1/channels/{self.created_channel_id}/messages", 
                                        data=message_data)
            if response.status_code == 201:
                data = response.json()
                if data.get("success"):
                    self.created_message_id = data["data"]["id"]
                    self.print_success(f"Message ID: {self.created_message_id}")
                else:
                    self.print_failure(data.get("message", "Unknown error"))
            else:
                self.print_failure(f"Status code: {response.status_code}")
        except Exception as e:
            self.print_failure(str(e))

    def test_get_messages(self):
        """메시지 목록 조회 테스트"""
        if not self.created_channel_id:
            self.print_warning("채널 ID가 없어 건너뜀")
            return
            
        self.print_header("20. 메시지 - 목록 조회")
        self.print_test("GET", f"/api/v1/channels/{self.created_channel_id}/messages")
        
        try:
            response = self.make_request("GET", f"/api/v1/channels/{self.created_channel_id}/messages",
                                        params={"page": 0, "size": 10})
            if response.status_code == 200:
                data = response.json()
                messages = data.get("messages", {}).get("content", [])
                self.print_success(f"메시지 개수: {len(messages)}")
            else:
                self.print_failure(f"Status code: {response.status_code}")
        except Exception as e:
            self.print_failure(str(e))

    def test_mark_message_read(self):
        """읽음 표시 테스트"""
        if not self.created_message_id:
            self.print_warning("메시지 ID가 없어 건너뜀")
            return
            
        self.print_header("21. 메시지 - 읽음 표시")
        self.print_test("POST", f"/api/v1/messages/{self.created_message_id}/read")
        
        try:
            response = self.make_request("POST", f"/api/v1/messages/{self.created_message_id}/read")
            if response.status_code == 200:
                data = response.json()
                if data.get("success"):
                    self.print_success(data.get("message", "읽음 표시 완료"))
                else:
                    self.print_failure(data.get("message", "Unknown error"))
            else:
                self.print_failure(f"Status code: {response.status_code}")
        except Exception as e:
            self.print_failure(str(e))

    def test_refresh_token(self):
        """토큰 갱신 테스트"""
        if not self.refresh_token:
            self.print_warning("Refresh token이 없어 건너뜀")
            return
            
        self.print_header("22. 인증 - 토큰 갱신")
        self.print_test("POST", "/api/v1/auth/refresh")
        
        refresh_data = {
            "refreshToken": self.refresh_token
        }
        
        try:
            response = self.make_request("POST", "/api/v1/auth/refresh", data=refresh_data)
            if response.status_code == 200:
                data = response.json()
                if data.get("success"):
                    self.access_token = data["data"]["accessToken"]
                    self.print_success("새 Access Token 발급 완료")
                else:
                    self.print_failure(data.get("message", "Unknown error"))
            else:
                self.print_failure(f"Status code: {response.status_code}")
        except Exception as e:
            self.print_failure(str(e))

    def test_logout(self):
        """로그아웃 테스트"""
        self.print_header("23. 인증 - 로그아웃")
        self.print_test("POST", "/api/v1/auth/logout")
        
        try:
            response = self.make_request("POST", "/api/v1/auth/logout")
            if response.status_code == 200:
                data = response.json()
                if data.get("success"):
                    self.print_success(data.get("message", "로그아웃 완료"))
                    self.access_token = None
                else:
                    self.print_failure(data.get("message", "Unknown error"))
            else:
                self.print_failure(f"Status code: {response.status_code}")
        except Exception as e:
            self.print_failure(str(e))

    def print_summary(self):
        """테스트 결과 요약 출력"""
        total = self.passed + self.failed
        success_rate = (self.passed / total * 100) if total > 0 else 0
        
        print(f"\n{Colors.BOLD}{'=' * 80}{Colors.RESET}")
        print(f"{Colors.BOLD}테스트 결과 요약{Colors.RESET}")
        print(f"{Colors.BOLD}{'=' * 80}{Colors.RESET}")
        print(f"총 테스트: {Colors.BOLD}{total}{Colors.RESET}")
        print(f"  {Colors.GREEN}✓ 성공: {self.passed}{Colors.RESET}")
        print(f"  {Colors.RED}✗ 실패: {self.failed}{Colors.RESET}")
        print(f"성공률: {Colors.BOLD}{Colors.GREEN if success_rate == 100 else Colors.RED}{success_rate:.1f}%{Colors.RESET}")
        print(f"총 소요 시간: {Colors.BOLD}{self.total_time:.2f}초{Colors.RESET}")
        print(f"{Colors.BOLD}{'=' * 80}{Colors.RESET}\n")

    def run_all_tests(self):
        """모든 테스트 실행"""
        print(f"{Colors.BOLD}{'CUPID API 통합 테스트'.center(80)}{Colors.RESET}")
        print(f"Base URL: {Colors.BOLD}{self.base_url}{Colors.RESET}\n")
        
        start_time = time.time()
        
        # 인증 없이 접근 가능한 API
        self.test_health_check()
        
        # 기존 사용자로 로그인 시도
        login_success = self.test_login_first()
        
        # 기존 사용자가 없으면 새로 가입
        if not login_success:
            self.print_info("기존 계정이 없으므로 새 계정을 생성합니다.")
            self.test_register()
            self.test_login()
        
        if not self.access_token:
            print(f"\n{Colors.RED}로그인 실패로 인해 인증이 필요한 테스트를 건너뜁니다.{Colors.RESET}\n")
            self.print_summary()
            return
        
        # 인증이 필요한 API
        self.test_validate_token()
        self.test_get_current_user()
        self.test_ping()
        self.test_update_profile()
        self.test_register_fcm_token()
        self.test_get_fcm_tokens()
        self.test_get_notification_settings()
        self.test_update_notification_settings()
        self.test_get_online_users()
        self.test_get_online_stats()
        self.test_get_channels()
        self.test_create_channel()
        self.test_get_channel_detail()
        self.test_send_message()
        self.test_get_messages()
        self.test_mark_message_read()
        
        # 비밀번호 변경
        self.test_change_password()
        
        # 비밀번호 변경 후 토큰이 무효화될 수 있으므로 재로그인
        if self.test_username == "gaheemm":
            login_data = {"username": "gaheemm", "password": "newtest123456"}
        else:
            login_data = {"username": self.test_username, "password": "newtest123456"}
        
        self.print_info("비밀번호 변경 후 재로그인")
        try:
            response = self.make_request("POST", "/api/v1/auth/login", data=login_data)
            if response.status_code == 200:
                data = response.json()
                if data.get("success") and "data" in data:
                    self.access_token = data["data"]["accessToken"]
                    self.refresh_token = data["data"]["refreshToken"]
                    self.print_success("재로그인 성공")
        except Exception as e:
            self.print_warning(f"재로그인 실패: {str(e)}")
        
        # 토큰 갱신
        self.test_refresh_token()
        
        # 로그아웃
        self.test_logout()
        
        self.print_summary()
        
        # 실패한 경우 종료 코드 1
        if self.failed > 0:
            sys.exit(1)
        else:
            sys.exit(0)

def main():
    parser = argparse.ArgumentParser(description='Cupid API 통합 테스트')
    parser.add_argument('--base-url', default='http://localhost:8080',
                       help='API 서버의 기본 URL (기본값: http://localhost:8080)')
    parser.add_argument('--timeout', type=int, default=30,
                       help='요청 타임아웃 (초) (기본값: 30)')
    parser.add_argument('--enable-rate-limit', action='store_true',
                       help='Rate limit 활성화 (기본값: 비활성화)')
    
    args = parser.parse_args()
    
    bypass_rate_limit = not args.enable_rate_limit  # 기본값은 True (비활성화)
    tester = ApiTester(base_url=args.base_url, bypass_rate_limit=bypass_rate_limit)
    tester.session.timeout = args.timeout
    
    try:
        tester.run_all_tests()
    except KeyboardInterrupt:
        print(f"\n{Colors.YELLOW}테스트가 중단되었습니다.{Colors.RESET}")
        sys.exit(1)
    except Exception as e:
        print(f"\n{Colors.RED}치명적 오류: {str(e)}{Colors.RESET}")
        sys.exit(1)

if __name__ == "__main__":
    main()

