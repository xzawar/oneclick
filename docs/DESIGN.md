**DESIGN.md**

On-Demand Local Services Marketplace · Lahore, Pakistan

**Design Process**

① Research → ② Define → ③ Ideate → ④ Design → ⑤ Validate

Stage \| Activities

Research \| InDrive UX study, competitor analysis, Pakistan user
behavior

Define \| User personas, pain points, user flows

Ideate \| Sketches, wireframes, component mapping

Design \| Visual identity, color tokens, UI screens

Validate \| MVP testing in Lahore, iterate

**Overview**

One Click is a map-first, trust-driven app where customers find and hire
verified local tradespeople instantly. Design is inspired by InDrive\'s
direct-contact model but tailored for Pakistan --- darker, bolder,
Urdu-ready.

Platform: Android · Language: Kotlin · MVP City: Lahore, Pakistan

**Problem Statement**

\"Finding a reliable plumber, electrician, or mechanic in Lahore means
asking friends, waiting hours, and never knowing the price until after
the job is done.\"

> • No centralized platform to find verified tradespeople nearby
>
> • No transparent pricing --- providers quote whatever they want
>
> • No safety mechanism --- customers cannot verify who shows up
>
> • Payments are cash-only with no accountability or escrow
>
> • No real-time tracking --- customer has no idea when provider arrives

**Solution**

> • Map-first discovery --- see all available providers near you in real
> time
>
> • Direct in-app calling --- talk before you book, no middleman
>
> • Fare negotiation --- agree on price before the job starts
> (InDrive-style)
>
> • Escrow wallet --- money held safely until job is confirmed complete
>
> • Live tracking --- watch provider travel to your location on the map
>
> • Verified providers --- CNIC + selfie verification, ratings, tier
> badges

**User Personas**

**Persona 1 --- The Customer**

**Hamza, 32 · Marketing Manager · Gulberg, Lahore**

Owns an apartment and car. Uses smartphone daily.

**MOTIVATIONS**

> • Fix things fast
>
> • Know price upfront
>
> • Safe, verified strangers at home
>
> • Track provider\'s arrival

**FRUSTRATIONS**

> • Cannot find a trusted plumber quickly
>
> • Quoted random prices after the job
>
> • No-shows with zero accountability
>
> • Cash only, no receipt, no proof

**GOALS**

> • Book a plumber in under 5 minutes
>
> • Know the agreed price before saying yes
>
> • Track who is coming to his home

**Persona 2 --- The Provider**

**Shahid, 28 · Self-employed Electrician · Johar Town, Lahore**

Works independently. Gets jobs via word of mouth only.

**MOTIVATIONS**

> • Find more customers without cold calling
>
> • Get paid fairly and securely
>
> • Build a verified online reputation
>
> • Stable, predictable income

**FRUSTRATIONS**

> • Slow days with no incoming work
>
> • Customers bargain after the job is done
>
> • No way to show skills or portfolio online
>
> • Cash disputes with no proof

**GOALS**

> • Appear to nearby customers automatically when online
>
> • Get paid securely before leaving the job site
>
> • Build a verified profile that earns trust and repeat customers

**User Flow**

**Customer Flow**

Open App

↓

Google Sign-In → Fill Name + Phone → Home Map

↓

Browse Map / Filter by Category

↓

Tap Provider Pin → View Profile Card

↓

Call Provider → Agree on Fare verbally

↓

Create Booking + Set Fare

↓

Provider Accepts → Escrow deducted from wallet

↓

Track Provider on Map → Provider Arrives

↓

Job in Progress (Escrow LOCKED)

↓ optional: Provider requests extra fee → Customer accepts/rejects

Provider uploads photo proof → Mark Complete

↓

Customer confirms completion → Escrow releases

↓

Rate Provider ⭐ → Done ✅

**Provider Flow**

Open App → Sign In → Complete Provider Form

↓

Admin Verification (CNIC + Selfie) → Verified ✅

↓

Switch to Provider Mode → Top Up Wallet (Rs.200+)

↓

Tap \"Go Online\" → Pin appears on customer maps

↓

Job Request Popup arrives (30-second timer)

↓ Accept / Reject

Navigate to customer → Mark Arrived → Do the job

↓

Upload Photo Proof → Mark Complete

↓

Customer Confirms → Earnings credited → Rate Customer ⭐

**Visual Identity**

**Color Palette**

PRIMARY COLORS

#1A1A2E Deep Navy --- Headers, primary background

#0F3D6B Dark Blue --- Buttons, active states

#2196F3 Bright Blue --- CTAs, highlights, links

#FFFFFF White --- Cards, surface, input backgrounds

STATUS COLORS

#4CAF50 Green --- Online, verified, success

#F44336 Red --- SOS, error, banned

#FF9800 Orange --- Warning, low wallet balance

#9E9E9E Gray --- Offline, disabled, metadata

#F5F5F5 LtGray --- App background

MAP PIN COLORS BY CATEGORY

#1565C0 Plumber #F9A825 Electrician

#BF360C Welder #37474F Mechanic

#4E342E Carpenter #6A1B9A Painter

#0F3D6B Default / Other

**Mode Visual Differentiation**

Customer Mode: Header = #0F3D6B (strong blue) --- focus on map + booking

Provider Mode: Header = #1A1A2E (deep dark) --- focus on online toggle +
jobs

Mode Toggle: Pill switch top-right corner, always visible

**Typography**

Font Families

Latin Text: Inter

Urdu Text: Noto Nastaliq Urdu

Monospace: JetBrains Mono (IDs, codes only)

Type Scale

display_large 32sp Bold Splash / hero screens

headline_large 24sp Bold Screen titles

headline_medium 20sp SemiBold Section headers

title_large 18sp SemiBold Card titles, provider name

title_medium 16sp Medium Button labels, list items

body_large 15sp Regular Primary body text

body_medium 13sp Regular Metadata, secondary text

label_large 12sp Medium Tags, chips, badges

label_small 10sp Regular Timestamps, captions

**Shapes & Spacing**

Corner Radius

4dp Tags, small chips

8dp Input fields

12dp Primary and secondary buttons

16dp Cards, bottom sheet top corners

24dp Large cards, modals

32dp Mode toggle pill

● Full circle --- avatars, SOS button, map pins

Spacing Scale

2dp Micro \| Icon internal padding

4dp Tiny \| Between tags

8dp Small \| Between inline elements

12dp Base \| Standard element gap

16dp Medium \| Section padding

24dp Large \| Card padding

32dp XL \| Screen horizontal margins

48dp XXL \| Hero sections

**Paper Sketches --- Key Screens**

**Sketch 1 --- Customer Home Map**

┌─────────────────────┐

│ ≡ One Click 🔔 │ \<- header

│ \[Customer\|Provider\]│ \<- mode toggle

├─────────────────────┤

│ │

│ MAP AREA │

│ 🔵Ahmad ⚡Sara │ \<- provider pins

│ 🔩Bilal │

│ 📍 │ \<- user location

├─────────────────────┤

│ \[🔧\]\[⚡\]\[🔩\]\[🚗\]\[+\]│ \<- category filter

└─────────────────────┘

Home Search Book Profile

**Sketch 2 --- Provider Card (Bottom Sheet)**

┌─────────────────────┐

│ \[Photo\] Ahmad Khan │

│ Plumber ✅ │ \<- verified

│ ⭐ 4.8 (127) │

│ 📍 1.2 km │

│ ┌──────┐ ┌──────┐ │

│ │ Call │ │ Book │ │

│ └──────┘ └──────┘ │

└─────────────────────┘

**Sketch 3 --- Job Request Popup (Provider)**

╔═════════════════════╗

║ 30s \[====\-\-\--\] ║ \<- countdown

║ New Job Request ║

║ Hamza · Plumbing ║

║ 1.4 km Rs.800 ║

║ ┌───────┐ ┌──────┐ ║

║ │Accept │ │Reject│ ║

║ └───────┘ └──────┘ ║

╚═════════════════════╝

**Sketch 4 --- Active Job Tracking (Customer)**

┌─────────────────────┐

│ Job in Progress │

│ MAP \[🔧\]\-\--\>\[📍\] │ \<- provider moving

├─────────────────────┤

│ Ahmad Khan │

│ ETA: 8 min 🕐 │

│ \[Message\] \[Call\] │

│ \[🚨 SOS\] │ \<- always visible

└─────────────────────┘

**Sketch 5 --- Wallet**

┌─────────────────────┐

│ My Wallet │

│ Balance: Rs.1,250 │

│ \[+ Top Up\] \[↑ Withdraw\]│

├─────────────────────┤

│ Transaction History │

│ Topup +500 ✅ │

│ Job #23 -120 💸 │

│ Payout +680 ✅ │

└─────────────────────┘

**Sketch 6 --- Provider Mode Home**

┌─────────────────────┐

│ \[Customer\|Provider\] │ \<- Provider active

│ MAP 📍 You │ \<- own pin visible

├─────────────────────┤

│ ● ONLINE │

│ Wallet: Rs.450 │

│ \[ Go Offline \] │

└─────────────────────┘

Map Jobs Earn Profile

**Wireframes**

**W1 --- Onboarding Flow**

Splash Details Ready

┌───────────┐ ┌───────────┐ ┌───────────┐

│ ⚡ │ │ Full Name │ │ ✅ │

│ One Click │► │ \[\_\_\_\_\_\_\_\] │► │ All set! │

│\[G\] Sign In│ │ Phone No │ │ \[Go Map\] │

└───────────┘ └───────────┘ └───────────┘

**W2 --- Provider Signup**

┌───────────────────────┐

│ Become a Provider │

│ Step 2 of 3 │

│ ▓▓▓▓▓▓▓░░░░░░ │

├───────────────────────┤

│ \[Plumber ✓\] │

│ \[Electrician \] │

│ \[Welder ✓\] │

│ Location: \[GPS/Text\] │

│ Shop Addr: \[Opt\] │

│ \[Continue →\] │

└───────────────────────┘

**W3 --- CNIC + Selfie Verification**

┌───────────────────────┐

│ Step 3 of 3 │

│ ▓▓▓▓▓▓▓▓▓▓▓▓▓░░░░ │

├───────────────────────┤

│ Upload CNIC │

│ \[ 📄 Tap to upload \] │

│ │

│ Take a Selfie │

│ \[ 📷 Camera only \] │

│ (no gallery allowed) │

│ │

│ \[Submit for Review\] │

│ Approved \~1 hour │

└───────────────────────┘

**W4 --- Create Booking**

┌───────────────────────┐

│ Book Ahmad Khan │

│ Service: \[Plumbing▾\] │

│ Issue: \[\_\_\_\_\_\_\_\_\_\_\] │

│ Photo: \[📷 Optional\] │

│ When: (●)Now ( )Later│

│ Offer: Rs.\[\_\_\_\_\_\] │

│ \[Send Request →\] │

└───────────────────────┘

**W5 --- Dispute Screen**

┌───────────────────────┐

│ Raise a Dispute │

│ Booking #456 │

│ Ahmad · Rs.800 escrow│

├───────────────────────┤

│ (●) Overcharged │

│ ( ) Poor workmanship │

│ ( ) No-show │

│ Evidence: \[📷\]\[📷\]\[+\] │

│ Details: \[\_\_\_\_\_\_\_\_\_\] │

│ ⚠ Escrow will freeze │

│ \[Submit Dispute\] │

└───────────────────────┘

**UI Screen Specifications**

**Screen 1 --- Splash**

Background #1A1A2E · Logo 80dp centered · Google Sign-In button white
fill

**Screen 2 --- Customer Map**

Header #0F3D6B · Mode toggle top-right · Category filter bar · 4-tab
bottom nav

**Screen 3 --- Provider Card**

White bottom sheet · Avatar 56dp circle · Blue verified badge · Call +
Book buttons

**Screen 4 --- Active Job**

Full-screen map · HUD #16213E 120dp · SOS 64dp red circle always visible

**Screen 5 --- Provider Online**

Header #1A1A2E (darker) · Own pin pulsing · Go Offline button prominent

**Screen 6 --- Wallet**

#0F3D6B balance card · Top Up + Withdraw buttons · Transaction history
list

**Component Library**

**Status Chips**

ONLINE #4CAF50 bg white text

ON JOB #2196F3 bg white text

OFFLINE #9E9E9E bg white text

VERIFIED #0F3D6B bg white text

**Tier Badges**

🥉 BRONZE \< 50 jobs or avg rating \< 4.0

🥈 SILVER 50--200 jobs and avg rating \>= 4.0

🥇 GOLD 200+ jobs and avg rating \>= 4.5

**Booking Status Pills**

\[pending\] #FF9800 orange

\[accepted\] #2196F3 blue

\[en_route\] #0F3D6B dark blue

\[in_progress\] #4CAF50 green (escrow locked)

\[completed\] #1A1A2E navy

\[cancelled\] #9E9E9E gray

\[disputed\] #F44336 red (escrow frozen)

**Toast Messages**

✅ \"Booking confirmed! Ahmad is on his way.\"

💸 \"Rs. 680 added to your wallet.\"

⚠ \"Wallet balance low. Top up to stay visible.\"

❌ \"Job request timed out.\"

🚨 \"SOS sent. Admin and contacts notified.\"

**Navigation Architecture**

App

├── Onboarding

│ ├── Splash

│ ├── Google Sign-In

│ ├── Name + Phone

│ └── Provider Setup (conditional)

│ ├── Categories + Location

│ ├── CNIC + Selfie

│ └── Pending Verification

│

├── Customer Mode

│ ├── Home Map

│ ├── Search / Filter by Category

│ ├── Provider Profile (full)

│ ├── Booking Creation

│ ├── Active Job Tracking

│ ├── Booking History

│ ├── Wallet

│ └── Profile / Settings

│

└── Provider Mode

├── Home Map (Go Online toggle)

├── Job Request Popup (overlay)

├── Active Job Screen

├── Job History

├── Earnings / Wallet

└── Profile / Portfolio / Categories

**Animation Guidelines**

Element \| Animation \| Duration \| Easing

Provider pin movement \| Position interpolation \| 300ms \| ease-in-out

Bottom sheet open \| Slide up \| 400ms \| spring

Job request popup \| Fade + slide up \| 250ms \| ease-out

Mode toggle pill \| Slide left/right \| 200ms \| ease-in-out

Timer bar depletion \| Linear shrink \| 30,000ms\| linear

Timer color shift \| Green \> Orange \> Red \| at 10s \| step

Map zoom on open \| Camera animate \| 500ms \| ease-in-out

Provider pin pulse \| Scale 1.0-1.3-1.0 loop \| 1500ms \| loop

**Accessibility**

> • Minimum touch target: 48x48dp on all interactive elements
>
> • Minimum contrast ratio: 4.5:1 on all body text
>
> • SOS requires long-press (not single tap) to prevent accidental
> triggers
>
> • All bottom nav tabs have icon + text label
>
> • Urdu (RTL) layout supported from day one (strings-ur.xml)
>
> • System font size scaling supported --- no hardcoded text sizes (use
> sp via the Compose theme)
>
> • All icons have contentDescription set for screen readers

**Design Handoff Checklist**

> • All colors in the Compose theme (ui/theme/Color.kt)
>
> • All text styles in the Compose theme (ui/theme/Type.kt)
>
> • All dimensions in ui/theme/Dimens.kt
>
> • All strings in strings.xml (Urdu in strings-ur.xml)
>
> • Category icons exported as ic_cat\_\*.xml (vector drawable)
>
> • Map pin drawables per category color
>
> • Dark / light theme variants for all surfaces
>
> • All bottom sheets use Material 3 ModalBottomSheet
>
> • SOS button always rendered at top z-order on job screens
>
> • Mode toggle accessible from every main screen
