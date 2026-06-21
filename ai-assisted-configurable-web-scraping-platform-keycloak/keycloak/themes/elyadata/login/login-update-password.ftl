<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1"/>
    <title>Update password — Elyadata</title>
    <style>
        *,*::before,*::after{box-sizing:border-box;margin:0;padding:0}
        html,body{height:100%;font-family:"Noto Sans",-apple-system,sans-serif;background:#F0F6FB;transition:background .3s,color .3s}
        .root{display:grid;grid-template-columns:1fr 1fr;height:100vh;overflow:hidden}
        .left{position:relative;overflow:hidden;background:#F0F6FB;display:flex;flex-direction:column;justify-content:space-between;padding:3rem 3.5rem;transition:background .3s}
        .left__bg{position:absolute;inset:0;width:100%;height:100%;z-index:0}
        .left__grid{position:absolute;inset:0;background-image:radial-gradient(circle,rgba(214,0,127,.13) 1px,transparent 1px);background-size:30px 30px;z-index:1;pointer-events:none}
        .left__pink-blob{position:absolute;bottom:-90px;left:-90px;width:340px;height:340px;border-radius:50%;background:radial-gradient(circle,rgba(214,0,127,.11) 0%,transparent 68%);z-index:1;pointer-events:none}
        .left__pink-ring{position:absolute;top:-55px;right:-55px;width:200px;height:200px;border-radius:50%;border:1.5px solid rgba(214,0,127,.14);z-index:1;pointer-events:none}
        .left__top{position:relative;z-index:2;display:flex;align-items:center;gap:.75rem}
        .left__mid{position:relative;z-index:2;flex:1;display:flex;flex-direction:column;justify-content:center;text-align:center;padding:3rem 0}
        .left__logo{height:80px;width:auto}
        .left__headline{font-size:3.2rem;font-weight:700;color:#000;line-height:1.15;letter-spacing:-.03em;margin-bottom:1.5rem;animation:fadeUp .7s ease both;transition:color .3s}
        .left__body{font-size:.96rem;color:#535353;line-height:1.8;font-weight:300;animation:fadeUp .7s .1s ease both;transition:color .3s}
        .hl-blue{color:#05BAEE}.hl-pink{color:#D6007F}
        .right{background:#fff;display:flex;flex-direction:column;align-items:center;justify-content:center;padding:2.5rem 3rem;position:relative;overflow-y:auto;transition:background .3s}
        .right__pink{position:absolute;bottom:-70px;right:-70px;width:220px;height:220px;border-radius:50%;background:radial-gradient(circle,rgba(214,0,127,.07) 0%,transparent 70%);pointer-events:none}
        .card{width:100%;max-width:380px;position:relative;z-index:1;animation:fadeUp .55s ease both}
        .card__logo{margin-bottom:1.75rem}
        .card__logo img{height:45px;width:auto}
        .card__title{font-size:2rem;font-weight:700;color:#000;letter-spacing:-.025em;margin-bottom:.3rem;transition:color .3s}
        .card__sub{font-size:.87rem;color:#535353;margin-bottom:1.75rem;transition:color .3s}
        .card__divider{height:1px;background:linear-gradient(90deg,#E4EDF5 60%,transparent 100%);margin-bottom:1.75rem;transition:background .3s}
        .card__footer{margin-top:1.75rem;display:flex;align-items:center;justify-content:center;gap:.5rem;font-size:.72rem;color:#535353;transition:color .3s}
        .card__footer-dot{width:3px;height:3px;border-radius:50%;background:#535353;display:inline-block}
        .alert{display:flex;align-items:center;gap:.45rem;padding:.6rem .9rem;border-radius:9px;font-size:.82rem;font-weight:500;margin-bottom:1rem}
        .alert--error{background:rgba(214,0,127,.05);border:1px solid rgba(214,0,127,.2);color:#D6007F;animation:shake .35s ease}
        .alert--warning{background:rgba(204,136,0,.06);border:1px solid rgba(204,136,0,.2);color:#CC8800}
        .alert--success{background:rgba(22,163,74,.07);border:1px solid rgba(22,163,74,.25);color:#16a34a}
        .field{margin-bottom:1.1rem}
        .field__label{display:block;font-size:.78rem;font-weight:600;color:#000;margin-bottom:.5rem;letter-spacing:.01em;transition:color .3s}
        .field__wrap{position:relative;display:flex;align-items:center}
        .field__input{width:100%;height:48px;padding:0 3rem 0 1rem;border:1.5px solid #E4EDF5;border-radius:10px;font-size:.9rem;color:#000;background:#FAFAFA;outline:none;transition:border-color .2s,box-shadow .2s,background .2s,color .2s;font-family:"Noto Sans",sans-serif}
        .field__input:focus{border-color:#05BAEE;background:#fff;box-shadow:0 0 0 3px rgba(214,0,127,.08)}
        .field__input::placeholder{color:#C8D5E2}
        .field__input--error{border-color:#D6007F}
        .field__error{display:block;font-size:.72rem;color:#D6007F;margin-top:.3rem}
        .field__btn{position:absolute;right:.7rem;background:none;border:none;cursor:pointer;color:#C8D5E2;display:flex;padding:5px;border-radius:5px;transition:color .2s}
        .field__btn:hover{color:#05BAEE}
        /* Password strength bar */
        .strength{margin-top:.5rem}
        .strength__bar{height:4px;border-radius:2px;background:#E4EDF5;overflow:hidden;margin-bottom:.3rem}
        .strength__fill{height:100%;border-radius:2px;transition:width .3s,background .3s;width:0}
        .strength__label{font-size:.68rem;color:#535353;transition:color .3s}
        .btn{width:100%;height:50px;display:flex;align-items:center;justify-content:center;gap:.5rem;border:none;border-radius:10px;background:#05BAEE;color:#fff;font-size:.92rem;font-weight:700;cursor:pointer;margin-top:1.5rem;letter-spacing:.03em;transition:background .2s,transform .15s,box-shadow .2s;box-shadow:0 4px 18px rgba(214,0,127,.2);font-family:"Noto Sans",sans-serif}
        .btn:hover{background:#04aad8;transform:translateY(-1px)}
        .btn:active{transform:translateY(0)}
        /* Autofill */
        .field__input:-webkit-autofill,
        .field__input:-webkit-autofill:hover,
        .field__input:-webkit-autofill:focus {
            -webkit-box-shadow:0 0 0 1000px #FAFAFA inset;
            -webkit-text-fill-color:#000;
            transition:background-color 5000s ease-in-out 0s;
        }

        /* ── Dark mode ── */
        html.kc-dark body  { background:#0F1824 }
        html.kc-dark .left { background:#0F1824 }
        html.kc-dark .left__grid { background-image:radial-gradient(circle,rgba(214,0,127,.08) 1px,transparent 1px) }
        html.kc-dark .left__headline { color:#E2EAF4 }
        html.kc-dark .left__body     { color:#94A3B8 }
        html.kc-dark .right          { background:#1A2233 }
        html.kc-dark .card__title    { color:#E2EAF4 }
        html.kc-dark .card__sub      { color:#94A3B8 }
        html.kc-dark .card__footer   { color:#94A3B8 }
        html.kc-dark .card__footer-dot { background:#94A3B8 }
        html.kc-dark .card__divider  { background:linear-gradient(90deg,#1E3048 60%,transparent 100%) }
        html.kc-dark .field__label   { color:#E2EAF4 }
        html.kc-dark .field__input   { background:#111827;color:#E2EAF4;border-color:#1E3048 }
        html.kc-dark .field__input:focus { background:#1A2233;border-color:#05BAEE }
        html.kc-dark .field__input::placeholder { color:#2D4560 }
        html.kc-dark .field__input:-webkit-autofill,
        html.kc-dark .field__input:-webkit-autofill:hover,
        html.kc-dark .field__input:-webkit-autofill:focus {
            -webkit-box-shadow:0 0 0 1000px #111827 inset;
            -webkit-text-fill-color:#E2EAF4;
        }
        html.kc-dark .strength__bar  { background:#1E3048 }
        html.kc-dark .strength__label { color:#94A3B8 }

        /* ── Logo switching ── */
        .logo--dark  { display:none }
        .logo--light { display:block }
        html.kc-dark .logo--dark  { display:block }
        html.kc-dark .logo--light { display:none }

        /* ── CSS variables ── */
        :root {
            --color-auth-bg:     #F0F6FB;
            --color-auth-blob-1: #BFE8F8;
            --color-auth-blob-2: #A8D8F0;
            --color-auth-blob-3: #F0B8D8;
        }
        html.kc-dark {
            --color-auth-bg:     #0F1824;
            --color-auth-blob-1: #0D2A3F;
            --color-auth-blob-2: #0A2035;
            --color-auth-blob-3: #2A0F1F;
        }
        @media (prefers-color-scheme: dark) {
            html:not(.kc-light) {
                --color-auth-bg:     #0F1824;
                --color-auth-blob-1: #0D2A3F;
                --color-auth-blob-2: #0A2035;
                --color-auth-blob-3: #2A0F1F;
            }
            html:not(.kc-light) body  { background:#0F1824 }
            html:not(.kc-light) .left { background:#0F1824 }
            html:not(.kc-light) .left__headline { color:#E2EAF4 }
            html:not(.kc-light) .left__body     { color:#94A3B8 }
            html:not(.kc-light) .right          { background:#1A2233 }
            html:not(.kc-light) .card__title    { color:#E2EAF4 }
            html:not(.kc-light) .card__sub      { color:#94A3B8 }
            html:not(.kc-light) .card__footer   { color:#94A3B8 }
            html:not(.kc-light) .card__footer-dot { background:#94A3B8 }
            html:not(.kc-light) .card__divider  { background:linear-gradient(90deg,#1E3048 60%,transparent 100%) }
            html:not(.kc-light) .field__label   { color:#E2EAF4 }
            html:not(.kc-light) .field__input   { background:#111827;color:#E2EAF4;border-color:#1E3048 }
            html:not(.kc-light) .field__input:focus { background:#1A2233;border-color:#05BAEE }
            html:not(.kc-light) .field__input::placeholder { color:#2D4560 }
            html:not(.kc-light) .logo--dark  { display:block }
            html:not(.kc-light) .logo--light { display:none }
        }

        @keyframes fadeUp{from{opacity:0;transform:translateY(14px)}to{opacity:1;transform:translateY(0)}}
        @keyframes shake{0%,100%{transform:translateX(0)}25%{transform:translateX(-5px)}75%{transform:translateX(5px)}}
        @media(max-width:820px){.root{grid-template-columns:1fr;height:auto}.left{display:none}.right{padding:2rem 1.5rem;min-height:100vh}}
    </style>

    <script>
        (function() {
            var params = new URLSearchParams(window.location.search);
            var theme  = params.get('theme');
            if (!theme) {
                theme = window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
            }
            document.documentElement.classList.add(theme === 'dark' ? 'kc-dark' : 'kc-light');
        })();
    </script>
</head>
<body>

<div class="root">
    <div class="left">
        <svg class="left__bg" viewBox="0 0 800 900" preserveAspectRatio="xMidYMid slice" aria-hidden="true">
            <defs>
                <radialGradient id="rg1" cx="30%" cy="20%" r="65%">
                    <stop offset="0%"   stop-color="var(--color-auth-blob-1)" stop-opacity=".95"/>
                    <stop offset="100%" stop-color="var(--color-auth-bg)"     stop-opacity="0"/>
                </radialGradient>
                <radialGradient id="rg2" cx="78%" cy="62%" r="55%">
                    <stop offset="0%"   stop-color="var(--color-auth-blob-2)" stop-opacity=".8"/>
                    <stop offset="100%" stop-color="var(--color-auth-bg)"     stop-opacity="0"/>
                </radialGradient>
                <radialGradient id="rg3" cx="10%" cy="90%" r="45%">
                    <stop offset="0%"   stop-color="var(--color-auth-blob-3)" stop-opacity=".22"/>
                    <stop offset="100%" stop-color="var(--color-auth-bg)"     stop-opacity="0"/>
                </radialGradient>
                <filter id="gb"><feGaussianBlur stdDeviation="38"/></filter>
            </defs>
            <rect width="800" height="900" fill="var(--color-auth-bg)"/>
            <ellipse cx="240" cy="180" rx="380" ry="300" fill="url(#rg1)" filter="url(#gb)"/>
            <ellipse cx="620" cy="558" rx="320" ry="260" fill="url(#rg2)" filter="url(#gb)"/>
            <ellipse cx="80"  cy="810" rx="260" ry="200" fill="url(#rg3)" filter="url(#gb)"/>
        </svg>
        <div class="left__grid"></div>
        <div class="left__pink-blob"></div>
        <div class="left__pink-ring"></div>
        <div class="left__top">
            <img class="left__logo logo--light" src="${url.resourcesPath}/img/elyadata.png"          alt="Elyadata" onerror="this.style.display='none'"/>
            <img class="left__logo logo--dark"  src="${url.resourcesPath}/img/ElyadataLogoWhite.png" alt="Elyadata" onerror="this.style.display='none'"/>
        </div>
        <div class="left__mid">
            <h1 class="left__headline">
                Secure your<br/>
                <span class="hl-blue">account</span><br/>
                now.
            </h1>
            <p class="left__body">Choose a strong password to protect your workspace.</p>
        </div>
    </div>

    <div class="right">
        <div class="right__pink"></div>
        <div class="card">

            <div class="card__logo">
                <img class="logo--light" src="${url.resourcesPath}/img/elyadata.png"          alt="Elyadata" onerror="this.style.display='none'"/>
                <img class="logo--dark"  src="${url.resourcesPath}/img/ElyadataLogoWhite.png" alt="Elyadata" onerror="this.style.display='none'"/>
            </div>

            <h2 class="card__title">Update password</h2>
            <p class="card__sub">Choose a new secure password</p>
            <div class="card__divider"></div>

            <#if message?has_content>
                <div class="alert alert--${message.type}">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
                        <#if message.type = 'error'>
                            <circle cx="12" cy="12" r="10"/>
                            <line x1="12" y1="8"  x2="12"    y2="12"/>
                            <line x1="12" y1="16" x2="12.01" y2="16"/>
                        <#elseif message.type = 'warning'>
                            <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/>
                            <line x1="12" y1="9" x2="12" y2="13"/>
                            <line x1="12" y1="17" x2="12.01" y2="17"/>
                        <#else>
                            <polyline points="20 6 9 17 4 12"/>
                        </#if>
                    </svg>
                    ${kcSanitize(message.summary)?no_esc}
                </div>
            </#if>

            <form action="${url.loginAction}" method="post">

                <#if isAppInitiatedAction??>
                    <input type="hidden" name="logout_sessions" value="on"/>
                </#if>

                <!-- ── New password ── -->
                <div class="field">
                    <label class="field__label" for="password-new">New password</label>
                    <div class="field__wrap">
                        <input id="password-new"
                               class="field__input ${messagesPerField.existsError('password-new')?then('field__input--error', '')}"
                               type="password" name="password-new"
                               placeholder="Min. 8 characters"
                               autocomplete="new-password"
                               autofocus
                               oninput="checkStrength(this.value)"/>
                        <button type="button" class="field__btn" onclick="togglePwd('password-new','eye1')" aria-label="Toggle">
                            <svg id="eye1" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                                <circle cx="12" cy="12" r="3"/>
                            </svg>
                        </button>
                    </div>
                    <#if messagesPerField.existsError('password-new')>
                        <span class="field__error">${kcSanitize(messagesPerField.get('password-new'))?no_esc}</span>
                    </#if>
                    <!-- Strength bar -->
                    <div class="strength">
                        <div class="strength__bar">
                            <div class="strength__fill" id="strength-fill"></div>
                        </div>
                        <span class="strength__label" id="strength-label"></span>
                    </div>
                </div>

                <!-- ── Confirm password ── -->
                <div class="field">
                    <label class="field__label" for="password-confirm">Confirm password</label>
                    <div class="field__wrap">
                        <input id="password-confirm"
                               class="field__input ${messagesPerField.existsError('password-confirm')?then('field__input--error', '')}"
                               type="password" name="password-confirm"
                               placeholder="••••••••"
                               autocomplete="new-password"/>
                        <button type="button" class="field__btn" onclick="togglePwd('password-confirm','eye2')" aria-label="Toggle">
                            <svg id="eye2" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                                <circle cx="12" cy="12" r="3"/>
                            </svg>
                        </button>
                    </div>
                    <#if messagesPerField.existsError('password-confirm')>
                        <span class="field__error">${kcSanitize(messagesPerField.get('password-confirm'))?no_esc}</span>
                    </#if>
                </div>

                <button type="submit" class="btn">
                    Update password
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                        <polyline points="20 6 9 17 4 12"/>
                    </svg>
                </button>

                <#if isAppInitiatedAction??>
                    <button type="submit" name="cancel-aia" value="true" class="btn" style="margin-top:.75rem;background:transparent;color:#535353;box-shadow:none;border:1.5px solid #E4EDF5;">
                        Cancel
                    </button>
                </#if>

            </form>

            <div class="card__footer">
                <span>© 2026 Elyadata</span>
                <span class="card__footer-dot"></span>
                <span>AI Scraping Platform</span>
            </div>
        </div>
    </div>
</div>

<script>
    function togglePwd(inputId, iconId) {
        var input = document.getElementById(inputId);
        var icon  = document.getElementById(iconId);
        if (input.type === 'password') {
            input.type = 'text';
            icon.innerHTML = '<path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94"/><path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19"/><line x1="1" y1="1" x2="23" y2="23"/>';
        } else {
            input.type = 'password';
            icon.innerHTML = '<path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/>';
        }
    }

    function checkStrength(val) {
        var fill  = document.getElementById('strength-fill');
        var label = document.getElementById('strength-label');
        if (!val) { fill.style.width = '0'; label.textContent = ''; return; }
        var score = 0;
        if (val.length >= 8)               score++;
        if (val.length >= 12)              score++;
        if (/[A-Z]/.test(val))             score++;
        if (/[0-9]/.test(val))             score++;
        if (/[^A-Za-z0-9]/.test(val))      score++;
        var levels = [
            { w: '20%', c: '#E24B4A', t: 'Too weak' },
            { w: '40%', c: '#EF9F27', t: 'Weak' },
            { w: '60%', c: '#EF9F27', t: 'Fair' },
            { w: '80%', c: '#05BAEE', t: 'Good' },
            { w: '100%',c: '#00904D', t: 'Strong' },
        ];
        var l = levels[Math.min(score - 1, 4)] || levels[0];
        fill.style.width      = l.w;
        fill.style.background = l.c;
        label.textContent     = l.t;
        label.style.color     = l.c;
    }
</script>

</body>
</html>
