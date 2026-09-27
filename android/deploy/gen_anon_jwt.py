"""签发客户端用的 anon JWT（填进 App 的 SUPABASE_ANON_KEY）。

PostgREST 靠这个 token 里的 `role` 决定把请求切成哪个数据库角色。
只用标准库实现 HS256，服务器上不必装任何 Python 包。

用法：
    python3 gen_anon_jwt.py <PGRST_JWT_SECRET>
输出的那一长串就是 ANON_KEY。
"""

import base64
import hashlib
import hmac
import json
import sys

# 默认签到 2035 年：这是给自家 App 用的固定凭据，不需要短期轮换，
# 短了反而会在某天突然「全部用户同时联不上」。
DEFAULT_EXP = 2080000000


def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def main() -> None:
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    secret = sys.argv[1].strip()
    exp = int(sys.argv[2]) if len(sys.argv) > 2 else DEFAULT_EXP

    header = {"alg": "HS256", "typ": "JWT"}
    payload = {"role": "anon", "iss": "dartvio", "iat": 1760000000, "exp": exp}

    signing_input = (
        b64url(json.dumps(header, separators=(",", ":")).encode())
        + "."
        + b64url(json.dumps(payload, separators=(",", ":")).encode())
    )
    sig = hmac.new(secret.encode(), signing_input.encode(), hashlib.sha256).digest()
    print(signing_input + "." + b64url(sig))


if __name__ == "__main__":
    main()
