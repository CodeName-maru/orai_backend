# Orai: 사내 메신저 및 협업 시스템

## 📌 프로젝트 개요
**Orai**는 기업 내부에서 원활한 커뮤니케이션을 지원하기 위한 사내 메신저 및 협업 도구입니다. 보안 문제와 커스터마이징의 한계를 극복하고자 직접 개발한 시스템으로, **실시간 채팅 서비스, 일정 관리, 알림 기능**을 통합하여 협업을 효율적으로 수행할 수 있도록 합니다.

## 🎯 프로젝트 목표
- **실시간 채팅 서비스 제공**: 1:1 채팅 및 그룹 채팅 기능 구현
- **캘린더 서비스 연동**: 일정 공유 및 알림 기능 추가
- **확장 가능한 시스템 구축**: 마이크로서비스 아키텍처 기반 설계
- **보안 및 접근 제어 강화**: 인증 및 권한 관리 시스템 적용
- **사용자 친화적인 UI/UX 설계**: 직관적이고 효율적인 디자인 제공

## :floppy_disk: 요구 사항 명세서
요구 사항 명세서는 https://pebble-mahogany-f56.notion.site/162398dd18748059b821df4866cb5f6c?v=162398dd18748019945c000c10819fdf 를 참고해주세요

## :pencil2: 와이어 프레임 및 스토리 보드
와이어 프레임 및 스토리 보드는 https://www.figma.com/design/dnxyVcV1NJuFEvqYvEg9lc/ORAI?node-id=0-1&p=f&t=fBthErmLA8ic0UpZ-0 를 참고 해주세요

## 🛠 기술 스택
### **백엔드**
- Java, Spring Boot, Spring Cloud, Spring Scheduler
- WebSocket, JPA, Redis, MongoDB
- AWS (EC2, S3, RDS, Lambda)
- Kubernetes, Docker, Jenkins, GitHub Actions

### **프론트엔드**
- React, Node.js, JavaScript
- CSS (Font-Awesome, Cursor), Styled-Component
- WebSocket 기반 실시간 데이터 처리

### **데이터베이스**
- MySQL, Redis, JPA, MongoDB, QuertDSL

### **협업 도구**
- Git, GitHub, Jira, Discord, Swagger, Figma

## 📂 프로젝트 구조
```
Orai/
├── Orai_backend/                  # 백엔드 소스 코드
│   ├── user-service/          # 사용자 관리 서비스
│   ├── chat-service/          # 채팅 서비스
│   ├── calendar-service/      # 캘린더 서비스
│   ├── admin-service/         # 관리자 기능
│   ├── etc-service/           # 기타 기능 (푸시 알림 등)
│   ├── config/                # 공통 설정 파일
│   └── gateway/               # API Gateway

├── ORAI_frontend/                 # 프론트엔드 소스 코드
│   ├── public/                # 정적 파일
│   ├── src/
│   │   ├── components/        # 재사용 가능한 컴포넌트
│   │   ├── pages/             # 주요 페이지
│   │   ├── services/          # API 통신 관련 코드
│   │   ├── store/             # 상태 관리 (Redux 등)
│   │   ├── utils/             # 유틸리티 함수 모음
│   │   └── App.js             # 루트 컴포넌트
│   └── package.json           # 프로젝트 종속성 목록

├── Notion/docs/                     # 문서 및 설계 자료
│   ├── 요구사항 정의서.pdf
│   ├── 프로젝트 기획서.pdf
│   ├── 시스템 아키텍처.pdf
│   ├── 단위 테스트 결과서.pdf
│   └── 통합 테스트 결과서.pdf
│
├── README.md                  # 프로젝트 소개 및 실행 방법
└── .gitignore                 # Git 관리 제외 파일
```

## ⚙️ 설치 및 실행 방법
### **1. 프로젝트 클론**
```sh
git clone https://github.com/your-repo/Orai.git
cd Orai
```

### **2. 백엔드 실행**
```sh
cd backend
./gradlew bootRun
```

### **3. 프론트엔드 실행**
```sh
cd frontend
npm install
npm start
```

## 📊 시스템 아키텍처
Orai 프로젝트는 **마이크로서비스 아키텍처(MSA)**를 기반으로 설계되었습니다. 주요 서비스는 독립적으로 배포되며, API Gateway를 통해 통합됩니다.

![Image](https://github.com/user-attachments/assets/a6e9e9a8-aae1-45ae-be71-04c6d7d9d4b9)

## ERD
![Image](https://github.com/user-attachments/assets/cfb490f3-766b-4117-a536-9a77cece9b5d)

**주요 서비스 구성:**
- **User Service**: 사용자 관리 및 인증 (JWT 기반)
![Image](https://github.com/user-attachments/assets/0fcb3038-6a55-485e-ad9d-c56e170341da)
- **Chat Service**: 실시간 채팅 기능 (WebSocket 기반)
![Image](https://github.com/user-attachments/assets/e94524ad-05ce-411e-9e77-2005cd6032d1)
- **Calendar Service**: 일정 관리 및 알림 기능
![Image](https://github.com/user-attachments/assets/92203217-b43f-4f51-8b38-70c05f358c4d)
- **Admin Service**: 관리자 기능 (권한 및 사용자 관리)
![Image](https://github.com/user-attachments/assets/e7f79a8c-3889-41fe-8ad8-39d5cfcc7888)
- **Etc Service**: 푸시 알림 및 기타 기능

## 🔍 테스트 및 품질 관리
본 프로젝트는 단위 테스트 및 통합 테스트를 통해 품질을 보장합니다.
- **단위 테스트**: JUnit, Mockito 활용
- **통합 테스트**: Swagger, Postman 활용
- **CI/CD**: GitHub Actions, Jenkins,  사용하여 자동 배포

테스트 결과는 https://pebble-mahogany-f56.notion.site/162398dd18748059b821df4866cb5f6c?v=162398dd18748019945c000c10819fdf 를 참고하세요.

## 🏗 프로젝트 일정
**2024년 12월 11일 ~ 2025년 2월 7일 (8주간 진행)**
| 단계  | 기간  | 세부 내용  |
| --- | --- | --- |
| 기획 및 요구사항 분석  | 12.11 ~ 12.17  | 기능 정의, 아키텍처 설계  |
| 디자인 및 와이어프레임  | 12.18 ~ 12.24  | UI/UX 설계, 프로토타입 제작  |
| 백엔드 개발  | 12.25 ~ 1.14  | API 개발, 데이터베이스 설계  |
| 프론트엔드 개발  | 1.15 ~ 1.28  | 화면 구현, API 연동  |
| 통합 테스트 및 수정  | 1.29 ~ 2.4  | 기능 테스트 및 디버깅  |
| 배포 및 안정화  | 2.5 ~ 2.7  | AWS 인프라 배포, 최종 점검  |

## :calendar: WBS
![Image](https://github.com/user-attachments/assets/0267a3ac-27ef-4349-bbbc-6c6cbd81fa1d)
![Image](https://github.com/user-attachments/assets/4e88bf5d-60aa-4ebf-b0df-596813840d85)
![Image](https://github.com/user-attachments/assets/a04f9d78-9618-46ed-9d51-881a6738e2f1)
![Image](https://github.com/user-attachments/assets/135d5ca4-82fe-4e72-a071-2595a9833a96)

## 🤝 기여 방법
1. 이슈를 확인하고 원하는 작업을 선택합니다.
2. 새로운 브랜치를 생성합니다. (`FLIX--oo-기능명`)
3. 변경 사항을 반영한 후 커밋합니다. (`git commit -m '기능 추가'`)
4. Pull Request(PR)을 생성하고 리뷰를 요청합니다.

## 📜 라이선스
이 프로젝트는 **MIT License**를 따릅니다.

---
문의 사항이나 피드백이 있으면 GitHub Issue를 통해 남겨주세요! 🚀
