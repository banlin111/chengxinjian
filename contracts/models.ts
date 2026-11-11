// 澄心涧 API contract 0.3.0; all dates ISO-8601, all IDs strings, money in fen.
export type ID = string;
export type DateTime = string;
export type DateOnly = string;
export type Theme = 'paper' | 'turquoise' | 'forest';
export type Platform = 'wechat_android' | 'wechat_ios' | 'wechat_harmony' | 'wechat_windows';
export type Empty = Record<string, never>;
export interface OK { success: boolean }
export interface PageQuery { page?: number; pageSize?: number }
export interface Page<T> { items: T[]; page: number; pageSize: number; total: number }
export interface Versioned { id: ID; version: number; createdAt: DateTime; updatedAt: DateTime }
export interface Reason { reason: string }
export interface VersionReason extends Reason { version: number }
export type PublishStatus = 'draft' | 'published' | 'archived';
export type ReviewStatus = 'pending' | 'approved' | 'rejected';
export type TargetKind = 'course' | 'lesson' | 'meditation' | 'replay';
export interface Target { kind: TargetKind; id: ID }
export type AccessPolicy = 'free' | 'member' | 'purchase' | 'member_or_purchase';
export interface Access { allowed: boolean; reason: 'free' | 'trial' | 'member' | 'purchased' | 'locked'; trialSeconds: number; expiresAt: DateTime | null }
export interface Money { amountFen: number; currency: 'CNY' }
export interface AgreementRef { type: 'privacy' | 'user' | 'membership'; version: string }
export interface Agreement extends AgreementRef { title: string; content: string; effectiveAt: DateTime }
export interface Capabilities { virtualPayment: boolean; physicalPayment: boolean; live: boolean; backgroundAudio: boolean; foregroundMixer: boolean; maxMixTracks: number; backgroundMix: 'none' | 'preset'; subscriptionMessages: boolean }
export interface Bootstrap { contractVersion: string; serverTime: DateTime; timezone: 'Asia/Shanghai'; defaultTheme: Theme; themes: Theme[]; capabilities: Capabilities; agreements: AgreementRef[]; support: { phone: string | null; hours: string; wechatCustomerServiceEnabled: boolean }; practiceRules: { minTimerSeconds: number; maxTimerSeconds: number; checkinSeconds: number; ruleVersion: string }; subscriptionTemplates: { purpose: 'live' | 'membership_expiry'; templateId: string }[] }
export interface Membership { active: boolean; planId: ID | null; planName: string | null; startsAt: DateTime | null; expiresAt: DateTime | null; lifetime: boolean }
export interface User { id: ID; nickname: string; avatarUrl: string | null; phoneMasked: string | null; hasPassword: boolean; roles: ('user' | 'teacher')[]; status: 'active' | 'banned' | 'deletion_pending'; membership: Membership; theme: Theme }
export interface AuthTokens { accessToken: string; refreshToken: string; expiresIn: number; refreshExpiresIn: number; tokenType: 'Bearer' }
export interface LoginResult extends AuthTokens { user: User; isNewUser: boolean; phoneBindingRequired: boolean }
export interface WxLogin { code: string; agreements: AgreementRef[] }
export interface SmsSend { phone: string; purpose: 'register' | 'login' | 'reset_password' | 'change_phone' | 'reauth'; captchaTicket: string }
export interface SmsReceipt { challengeId: ID; retryAfterSeconds: number; expiresIn: number }
export interface SmsProof { challengeId: ID; code: string }
export interface Reauth extends SmsProof { purpose: 'change_phone' | 'change_password' | 'delete_account' }
export interface ReauthResult { reauthToken: string; expiresIn: number }
export interface Deletion { id: ID; status: 'pending' | 'cancelled' | 'completed' | 'blocked'; requestedAt: DateTime; effectiveAt: DateTime | null; blockers: string[] }
// version is returned by the server; send it on PUT for optimistic locking. Omission retains v0.1 compatibility.
export interface Settings { version?: number; theme: Theme; backgroundAudio: boolean; defaultSpeed: number; autoNext: boolean; endBellAssetId: ID | null; practiceReminderEnabled: boolean; practiceReminderTime: string | null; liveReminderEnabled: boolean; membershipReminderEnabled: boolean }
export interface Notice { id: ID; title: string; content: string; publishedAt: DateTime; read: boolean }
export interface Category extends Versioned { name: string; kind: 'course' | 'meditation' | 'noise'; sort: number; status: PublishStatus }
export interface Teacher extends Versioned { name: string; avatarUrl: string | null; bio: string; credentials: string[]; followed: boolean; status: PublishStatus }
export interface Course extends Versioned { title: string; summary: string; description: string; coverUrl: string; categoryId: ID; teacherId: ID; difficulty: 'beginner' | 'intermediate' | 'advanced'; totalSeconds: number; lessonCount: number; priceFen: number; originalPriceFen: number | null; accessPolicy: AccessPolicy; access: Access; favorite: boolean; liked: boolean; likeCount: number; learnerCount: number; progressPercent: number; status: PublishStatus }
export interface Lesson extends Versioned { courseId: ID; title: string; sort: number; durationSeconds: number; mediaKind: 'audio' | 'video'; trialSeconds: number; access: Access; resumeSeconds: number; completed: boolean; status: PublishStatus }
export interface Meditation extends Versioned { title: string; summary: string; coverUrl: string; categoryId: ID; teacherId: ID | null; durationSeconds: number; accessPolicy: 'free' | 'member'; access: Access; favorite: boolean; status: PublishStatus }
export interface Noise extends Versioned { title: string; coverUrl: string; categoryId: ID; durationSeconds: number; accessPolicy: 'free' | 'member'; access: Access; status: PublishStatus }
export interface MixTrack { noiseId: ID; volume: number }
export interface MixPreset extends Versioned { title: string; tracks: MixTrack[]; backgroundAssetReady: boolean; status: PublishStatus }
export interface LearningItem { target: Target; title: string; coverUrl: string; courseId: ID | null; lessonId: ID | null; positionSeconds: number; progressPercent: number; completed: boolean; lastPlayedAt: DateTime }
export interface Home { greeting: string; continueLearning: LearningItem | null; recommendedCourses: Course[]; recommendedMeditations: Meditation[]; newCourses: Course[]; notices: Notice[]; banners: Banner[]; checkedInToday: boolean; streakDays: number }
export interface Banner { id: ID; imageUrl: string; title: string; destination: Target | { kind: 'live'; id: ID } | { kind: 'membership'; id: ID }; sort: number }
export type SearchHit = { kind: 'course'; item: Course } | { kind: 'meditation'; item: Meditation } | { kind: 'teacher'; item: Teacher } | { kind: 'live'; item: Live };
export interface SearchQuery extends PageQuery { q: string; kind?: 'all' | 'course' | 'meditation' | 'teacher' | 'live' }
export interface ContentQuery extends PageQuery { categoryId?: ID; teacherId?: ID; difficulty?: 'beginner' | 'intermediate' | 'advanced'; minSeconds?: number; maxSeconds?: number; memberOnly?: boolean; sort?: 'recommended' | 'newest' | 'duration_asc' }
export interface PlaybackRequest { target: Target | { kind: 'noise' | 'mix_preset'; id: ID }; platform: Platform }
export interface Playback { playbackId: ID; url: string; expiresAt: DateTime; durationSeconds: number; resumeSeconds: number; access: Access; mediaKind: 'audio' | 'video'; heartbeatSeconds: number }
export interface PlaybackEvent { eventId: ID; sequence: number; kind: 'playing' | 'pause' | 'seek' | 'ended' | 'background' | 'foreground'; positionSeconds: number; playedDeltaSeconds: number; speed: number; occurredAt: DateTime }
export interface PlaybackAck { acceptedSequence: number; positionSeconds: number; creditedSeconds: number; completed: boolean }
export interface PracticeStart { kind: 'guided' | 'timer' | 'noise'; meditationId?: ID; plannedSeconds: number; tracks: MixTrack[]; mixPresetId?: ID; endBellAssetId?: ID }
export interface Practice extends Versioned { kind: 'guided' | 'timer' | 'noise'; status: 'active' | 'paused' | 'completed' | 'abandoned'; startedAt: DateTime; endedAt: DateTime | null; plannedSeconds: number; creditedSeconds: number; checkinEligible: boolean; heartbeatSeconds: number }
export interface PracticeEvent { eventId: ID; sequence: number; action: 'heartbeat' | 'pause' | 'resume' | 'finish' | 'abandon'; activeDeltaSeconds: number; occurredAt: DateTime }
export interface PracticeStats { from: DateOnly; to: DateOnly; totalSeconds: number; completedSessions: number; streakDays: number; checkinDays: number; daily: { date: DateOnly; seconds: number; checkedIn: boolean }[] }
export interface Comment extends Versioned { target: Target; author: { id: ID; nickname: string; avatarUrl: string | null; isTeacher: boolean }; parentId: ID | null; text: string; status: ReviewStatus | 'deleted'; liked: boolean; likeCount: number; replyCount: number }
export interface CommentQuery extends PageQuery { kind: TargetKind; targetId: ID; parentId?: ID; sort?: 'newest' | 'popular' | 'teacher' }
export interface CommentCreate { target: Target; parentId?: ID; text: string }
export interface Report extends Versioned { targetType: 'comment' | 'live_message' | 'submission'; targetId: ID; reason: string; status: 'pending' | 'resolved' | 'rejected' }
export interface Live extends Versioned { title: string; description: string; coverUrl: string; teacherId: ID; scheduledAt: DateTime; startedAt: DateTime | null; endedAt: DateTime | null; status: 'scheduled' | 'live' | 'ended' | 'cancelled' | 'forced_offline'; accessPolicy: 'free' | 'member'; access: Access; reserved: boolean; viewerCount: number; replayEnabled: boolean; replayId: ID | null; commentsEnabled: boolean }
export interface LiveJoin { participationId: ID; playbackUrl: string; playbackExpiresAt: DateTime; websocketUrl: string; ticket: string; ticketExpiresAt: DateTime; heartbeatSeconds: number; mutedUntil: DateTime | null; commentsEnabled: boolean }
export interface LiveMessage { id: ID; liveId: ID; userId: ID; nickname: string; role: 'user' | 'teacher' | 'moderator'; kind: 'comment' | 'question' | 'reply'; text: string; replyToId: ID | null; status: ReviewStatus | 'deleted'; createdAt: DateTime }
export interface Replay extends Versioned { liveId: ID; title: string; coverUrl: string; teacherId: ID; durationSeconds: number; accessPolicy: 'free' | 'member'; access: Access; chapters: { title: string; startSeconds: number }[]; status: 'processing' | 'ready' | 'failed' | 'hidden' }
export interface MembershipPlan extends Versioned { name: string; period: 'month' | 'quarter' | 'year' | 'lifetime'; durationDays: number | null; priceFen: number; originalPriceFen: number | null; benefits: string[]; status: PublishStatus }
export interface Entitlement { id: ID; kind: 'membership' | 'course'; targetId: ID; source: 'payment' | 'reward' | 'admin'; startsAt: DateTime; expiresAt: DateTime | null; status: 'active' | 'revoked' | 'expired' }
export type OrderItem = { kind: 'membership'; planId: ID } | { kind: 'course'; courseId: ID };
export interface QuoteRequest { item: OrderItem; platform: Platform }
export interface Quote { quoteId: ID; expiresAt: DateTime; item: OrderItem; title: string; totalFen: number; payableFen: number; currency: 'CNY'; paymentChannel: 'wechat_virtual' | 'unavailable'; unavailableReason: string | null; agreement: AgreementRef; priceVersion: number }
export type OrderStatus = 'pending_payment' | 'paid' | 'fulfilling' | 'completed' | 'cancelled' | 'closed';
export type RefundStatus = 'none' | 'requested' | 'approved' | 'processing' | 'succeeded' | 'rejected' | 'failed' | 'external_pending';
export interface Order extends Versioned { orderNo: string; kind: 'membership' | 'course' | 'reward_physical' | 'reward_course'; title: string; totalFen: number; paidFen: number; currency: 'CNY'; status: OrderStatus; refundStatus: RefundStatus; paymentChannel: 'wechat_virtual' | 'none'; providerRoute: 'wechat' | 'apple' | null; paymentExpiresAt: DateTime | null; paidAt: DateTime | null; item: OrderItem | { kind: 'reward'; claimId: ID }; entitlementIds: ID[]; shipmentId: ID | null; allowedActions: ('pay' | 'cancel' | 'refund' | 'track' | 'confirm_receipt')[] }
export interface VirtualPayment { paymentId: ID; channel: 'wechat_virtual'; expiresAt: DateTime; params: { mode: 'short_series_goods'; signData: string; paySig: string; signature: string } }
export interface PaymentState { paymentId: ID; status: 'pending' | 'succeeded' | 'failed' | 'closed'; orderId: ID; providerRoute: 'wechat' | 'apple' | null }
export interface Refund extends Versioned { orderId: ID; amountFen: number; reason: string; status: Exclude<RefundStatus, 'none'>; route: 'merchant' | 'apple'; instructions: string | null; rejectionReason: string | null }
export interface AddressInput { recipient: string; phone: string; provinceCode: string; cityCode: string; districtCode: string; detail: string; isDefault: boolean }
export interface Address extends AddressInput, Versioned {}
export interface Shipment extends Versioned { orderId: ID; carrierCode: string; carrierName: string; trackingNo: string; status: 'pending' | 'shipped' | 'in_transit' | 'delivered' | 'exception'; recipientMasked: string; phoneMasked: string; addressMasked: string; events: { time: DateTime; description: string; status: string }[]; lastSyncedAt: DateTime | null }
export interface RewardRule extends Versioned { name: string; trigger: 'streak' | 'submission'; requiredDays: number | null; kind: 'physical' | 'course'; courseId: ID | null; physicalName: string | null; coverUrl: string; stockRemaining: number | null; startAt: DateTime; endAt: DateTime | null; perUserLimit: number; requiresReview: boolean; status: PublishStatus }
export interface RewardEligibility { rule: RewardRule; eligible: boolean; progressDays: number; reason: string | null; claimId: ID | null }
export interface Checkin { date: DateOnly; streakDays: number; creditedSeconds: number; alreadyCheckedIn: boolean; rewards: RewardEligibility[] }
export interface RewardClaim extends Versioned { ruleId: ID; ruleVersion: number; submissionId: ID | null; kind: 'physical' | 'course'; status: 'pending_address' | 'processing' | 'issued' | 'shipped' | 'completed' | 'rejected'; orderId: ID | null; shipmentId: ID | null; addressId: ID | null; rejectionReason: string | null }
export interface Submission extends Versioned { assetId: ID; practiceSessionId: ID; text: string; status: ReviewStatus; reviewReason: string | null; rewardClaimIds: ID[] }
export interface Asset extends Versioned { purpose: 'avatar' | 'cover' | 'course_media' | 'meditation_media' | 'noise_media' | 'practice_recording'; filename: string; mimeType: string; sizeBytes: number; durationSeconds: number | null; status: 'uploading' | 'processing' | 'ready' | 'rejected'; publicUrl: string | null }
export interface UploadRequest { purpose: Asset['purpose']; filename: string; mimeType: string; sizeBytes: number; sha256: string }
// Submit every returned formData entry verbatim; never rename provider signature fields.
export interface UploadTicket { assetId: ID; uploadUrl: string; method: 'POST'; formData: Record<string, string>; fileField: 'file'; expiresAt: DateTime; maxSizeBytes: number }
export interface Job extends Versioned { kind: 'export' | 'transcode' | 'replay_to_course' | 'account_export'; status: 'queued' | 'running' | 'succeeded' | 'failed'; progress: number; resultUrl: string | null; resultId: ID | null; expiresAt: DateTime | null; errorCode: string | null }
export interface AdminProfile { id: ID; username: string; displayName: string; roleIds: ID[]; permissions: string[]; dataScope: 'all' | 'own_content'; teacherId: ID | null }
export interface AdminLogin { username: string; password: string; captchaTicket: string; captchaRandstr?: string; otp?: string }
export interface AdminAuth extends AuthTokens { admin: AdminProfile }
export interface AdminUser extends User, Versioned { registeredAt: DateTime; lastLoginAt: DateTime | null; totalPracticeSeconds: number; streakDays: number }
export interface UserQuery extends PageQuery { phone?: string; registeredFrom?: DateOnly; registeredTo?: DateOnly; status?: User['status']; membershipActive?: boolean }
export interface UserActivity { learning: Page<LearningItem>; practice: PracticeStats; liveParticipations: Page<{ liveId: ID; joinedAt: DateTime; watchedSeconds: number }> }
export interface GrantInput { kind: 'membership' | 'course'; targetId: ID; days?: number; lifetime?: boolean; reason: string }
export interface Grant extends Versioned { userId: ID; entitlement: Entitlement; reason: string; operatorId: ID }
export interface AdminCourseInput { title: string; summary: string; description: string; coverAssetId: ID; categoryId: ID; teacherId: ID; difficulty: Course['difficulty']; priceFen: number; originalPriceFen: number | null; accessPolicy: AccessPolicy; status: PublishStatus }
export interface AdminLessonInput { courseId: ID; title: string; sort: number; assetId: ID; trialSeconds: number; status: PublishStatus }
export interface AdminMeditationInput { title: string; summary: string; coverAssetId: ID; categoryId: ID; teacherId: ID | null; assetId: ID; accessPolicy: 'free' | 'member'; status: PublishStatus }
export interface AdminNoiseInput { title: string; coverAssetId: ID; categoryId: ID; assetId: ID; accessPolicy: 'free' | 'member'; status: PublishStatus }
export interface AdminCategoryInput { name: string; kind: Category['kind']; sort: number; status: PublishStatus }
export interface AdminTeacherInput { name: string; avatarAssetId: ID | null; bio: string; credentials: string[]; userId: ID | null; status: PublishStatus }
export interface AdminPlanInput { name: string; period: MembershipPlan['period']; durationDays: number | null; priceFen: number; originalPriceFen: number | null; benefits: string[]; status: PublishStatus }
export interface AdminMixInput { title: string; tracks: MixTrack[]; status: PublishStatus }
export interface AdminRewardInput { name: string; trigger: RewardRule['trigger']; requiredDays: number | null; kind: RewardRule['kind']; courseId: ID | null; physicalName: string | null; coverAssetId: ID; stock: number | null; startAt: DateTime; endAt: DateTime | null; perUserLimit: number; requiresReview: boolean; status: PublishStatus }
export interface AdminNoticeInput { title: string; content: string; publishedAt: DateTime; status: PublishStatus }
export interface AdminNotice extends AdminNoticeInput, Versioned {}
export interface ContentMap {
  courses: { input: AdminCourseInput; output: Course & { coverAssetId: ID } };
  lessons: { input: AdminLessonInput; output: Lesson & { assetId: ID } };
  meditations: { input: AdminMeditationInput; output: Meditation & { coverAssetId: ID; assetId: ID } };
  noises: { input: AdminNoiseInput; output: Noise & { coverAssetId: ID; assetId: ID } };
  categories: { input: AdminCategoryInput; output: Category };
  teachers: { input: AdminTeacherInput; output: Teacher & { avatarAssetId: ID | null; userId: ID | null } };
  'membership-plans': { input: AdminPlanInput; output: MembershipPlan };
  'mix-presets': { input: AdminMixInput; output: MixPreset };
  'reward-rules': { input: AdminRewardInput; output: RewardRule & { coverAssetId: ID } };
  notices: { input: AdminNoticeInput; output: AdminNotice };
}
export interface AdminContentQuery extends PageQuery { q?: string; status?: PublishStatus; categoryId?: ID; courseId?: ID; teacherId?: ID }
export interface AdminLiveInput { title: string; description: string; coverAssetId: ID; teacherId: ID; scheduledAt: DateTime; accessPolicy: 'free' | 'member'; replayEnabled: boolean; commentsEnabled: boolean }
export interface StreamCredential { pushUrl: string; streamKey: string; expiresAt: DateTime }
export interface ReviewDecision { decision: 'approve' | 'reject'; reason: string; version: number }
export interface HomeConfig { version: number; defaultTheme: Theme; themes: Theme[]; recommendedCourseIds: ID[]; recommendedMeditationIds: ID[]; recommendedPracticeIds: ID[]; newCourseIds: ID[]; banners: { id: ID; imageAssetId: ID; title: string; destination: Banner['destination']; sort: number }[]; hotKeywords: string[] }
export interface PracticeConfig { version: number; timerPresetsSeconds: number[]; minTimerSeconds: number; maxTimerSeconds: number; checkinSeconds: number; maxMixTracks: number; allowedEndBellAssetIds: ID[]; ruleVersion: string }
export interface ReminderConfig { version: number; membershipBeforeDays: number[]; liveBeforeMinutes: number; templateIds: { purpose: 'live' | 'membership_expiry'; templateId: string }[] }
export interface ModerationConfig { version: number; blockedKeywords: string[]; commentsPreReview: boolean; recordingMaxSeconds: number; recordingMaxBytes: number }
export interface Role extends Versioned { name: string; permissions: string[]; dataScope: 'all' | 'own_content' }
export interface RoleInput { name: string; permissions: string[]; dataScope: Role['dataScope'] }
export interface AdminAccountInput { username: string; displayName: string; roleIds: ID[]; teacherId: ID | null; enabled: boolean }
export interface AdminAccount extends AdminAccountInput, Versioned {}
export interface Permission { code: string; name: string; module: string }
export interface FeatureConfig { version: number; liveEnabled: boolean; recordingRewardEnabled: boolean; membershipSalesEnabled: boolean; courseSalesEnabled: boolean }
export interface AuditLog { id: ID; operatorId: ID; action: string; targetId: ID; reason: string | null; requestId: string; at: DateTime }
export interface MetricQuery { from: DateOnly; to: DateOnly; granularity?: 'day' | 'week' | 'month' }
export interface Metrics { from: DateOnly; to: DateOnly; timezone: 'Asia/Shanghai'; daily: { date: DateOnly; dau: number; newUsers: number; practiceSeconds: number; coursePlays: number; liveViewers: number; paidFen: number; refundedFen: number; membershipPaidFen: number; newMembers: number }[]; retention: { cohortDate: DateOnly; cohortUsers: number; d1: number | null; d7: number | null; d30: number | null }[] }
export interface FinanceQuery extends PageQuery { from?: DateOnly; to?: DateOnly; kind?: Order['kind']; status?: OrderStatus; refundStatus?: RefundStatus; userId?: ID; orderNo?: string }
export interface FinanceSummary { from: DateOnly; to: DateOnly; grossPaidFen: number; refundsFen: number; providerFeesFen: number; netReceiptsFen: number; unsettledFen: number; paidOrderCount: number; membershipSales: { planId: ID; count: number; grossFen: number }[]; reconciliationStatus: 'pending' | 'matched' | 'exceptions' }
export interface Reconciliation extends Versioned { date: DateOnly; channel: 'wechat_virtual'; status: 'pending' | 'matched' | 'exceptions' | 'failed'; mismatches: { orderNo: string; reason: string }[] }
export interface ModerationQueueQuery extends PageQuery { status?: ReviewStatus }
export interface SubscriptionConsent { purpose: 'live' | 'membership_expiry'; templateId: string; result: 'accept' | 'reject' | 'ban'; liveId?: ID }
export type SocketEvent =
  | { type: 'auth'; ticket: string; lastEventId?: ID }
  | { type: 'heartbeat'; clientTime: DateTime }
  | { type: 'ready'; eventId: ID; serverTime: DateTime }
  | { type: 'message.published'; eventId: ID; message: LiveMessage }
  | { type: 'message.deleted'; eventId: ID; messageId: ID }
  | { type: 'live.stats'; eventId: ID; viewers: number; likes: number }
  | { type: 'live.state'; eventId: ID; status: Live['status']; commentsEnabled: boolean }
  | { type: 'user.muted'; eventId: ID; until: DateTime | null }
  | { type: 'access.revoked'; eventId: ID; reason: string }
  | { type: 'resync_required'; eventId: ID }
  | { type: 'error'; code: string; message: string };

export interface AssetProcessing { assetId: ID; status: Asset['status']; reviewStatus: 'pending' | 'approved' | 'rejected'; jobState: 'pending' | 'running' | 'done' | 'failed' | null; attempts: number; errorCode: string | null; trials: { seconds: number; state: 'pending' | 'running' | 'ready' | 'failed'; attempts: number; errorCode: string | null }[] }
