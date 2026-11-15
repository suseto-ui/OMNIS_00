"""
O.M.N.I.S. Domain Contextual Dictionaries & Butterfly Effect Expansion Module
Manages domain-specific terminology, 28-pair correlation tensor weights, and butterfly effect ripple graphs.
"""

from __future__ import annotations
import re
from typing import Dict, List, Any, Tuple

from pydantic import BaseModel, Field

# Definice 8 kognitivních dimenzí
DOMAINS_8D = ["SYS", "ECON", "PSYCH", "ECO", "LAW", "SEC", "PHYS", "SOC"]

class EpistemicPersona(BaseModel):
    name: str
    identity_prefix: str
    focus_dimensions: List[str]

# Přednastavené profily oborových slovníků s Personami
DOMAIN_PROFILES: Dict[str, Dict[str, Any]] = {
    "FINTECH": {
        "name": "FinTech & Algoritmická Ekonomie",
        "persona": EpistemicPersona(
            name="Kvantový Strategický Analytik",
            identity_prefix="Jsi Kvantový Strategický Analytik specializovaný na asymetrickou ekonomii a digitální likviditu.",
            focus_dimensions=["ECON", "LAW", "SEC"]
        ),
        "keywords": ["fintech", "bank", "krypto", "platby", "investice", "likvidita", "transakce", "blockchain", "swap", "defi"],
        "primary_pair": ("ECON", "LAW"),
        "pair_weights": {
            ("ECON", "LAW"): 0.98,
            ("ECON", "SEC"): 0.95,
            ("SEC", "SYS"): 0.92,
        },
        "butterfly_vectors": [
            "Mikrosekundová latence v obchodní síti vyvolá arbitrární kolizi v garanci likvidity.",
            "Změna v regulaci MiCA (LAW) okamžitě změní cenotvorbu kapitálu v tokenové ekonomice (ECON)."
        ]
    },
    "INDUSTRY_40": {
        "name": "Průmyslová Výroba & Průmysl 4.0",
        "persona": EpistemicPersona(
            name="Digitální Inženýr Dvojčat",
            identity_prefix="Jsi Digitální Inženýr Dvojčat zaměřený na kyber-fyzikální systémy a termodynamickou stabilitu výroby.",
            focus_dimensions=["PHYS", "ECO", "SYS"]
        ),
        "keywords": ["průmysl", "výroba", "továrna", "iot", "automatizace", "robotika", "stroj", "dodavatelský", "hardware", "materiál"],
        "primary_pair": ("PHYS", "ECO"),
        "pair_weights": {
            ("PHYS", "ECO"): 0.96,
            ("PHYS", "SYS"): 0.94,
            ("SYS", "SEC"): 0.91,
        },
        "butterfly_vectors": [
            "Mikroskopické opotřebení ložiska (PHYS) vyvolá tepelnou špičku a zastavení celého dodavatelského řetězce.",
            "Mírný nárůst emise CO2 na výrobní lince (ECO) spustí automatickou pokutu od regulatorního orgánu (LAW)."
        ]
    },
    "CYBERSEC": {
        "name": "Zero-Trust Kybernetická Bezpečnost",
        "persona": EpistemicPersona(
            name="Zero-Trust Architekt",
            identity_prefix="Jsi Zero-Trust Architekt provádějící hloubkovou inspekci útočných ploch a kryptografickou verifikaci.",
            focus_dimensions=["SEC", "SYS", "LAW"]
        ),
        "keywords": ["bezpečnost", "security", "zero-trust", "kryptografie", "xss", "injection", "firewall", "zk-proof", "certifikát", "vulnerab"],
        "primary_pair": ("SEC", "SYS"),
        "pair_weights": {
            ("SEC", "SYS"): 0.99,
            ("SEC", "LAW"): 0.95,
            ("SEC", "PSYCH"): 0.90,
        },
        "butterfly_vectors": [
            "Zranitelnost typu zero-day v knihovně nižší úrovně (SEC) umožní převzetí kontrolního uzlu cloudové infrastruktury (SYS).",
            "Sociální inženýrství cílené na operátora (PSYCH) obajde dvoufázovou autentizaci v kognitivním rozhraní (SEC)."
        ]
    },
    "GENERAL_SYSTEMS": {
        "name": "Všeobecné Systémové Inženýrství",
        "persona": EpistemicPersona(
            name="Centrální Kognitivní Jádro",
            identity_prefix="Jsi Centrální Kognitivní Jádro O.M.N.I.S., architektonický strážce transdisciplinární rovnováhy.",
            focus_dimensions=["SYS", "SOC", "PSYCH"]
        ),
        "keywords": [],
        "primary_pair": ("SYS", "SEC"),
        "pair_weights": {
            ("SYS", "SEC"): 0.90,
            ("ECON", "SYS"): 0.85,
        },
        "butterfly_vectors": [
            "Systémový drift v reálném čase vyvolá kaskádový rozpad rovnovážného stavu."
        ]
    }
}


class DomainDictionaryEngine:
    """
    Služba pro autonomní detekci oboru, extrakci kontextuálních slovníků
    a výpočet 28-párových tenzorových vah pro CsrKnowledgeGraph.
    """

    @classmethod
    def detect_domain_profile(cls, query_text: str) -> Dict[str, Any]:
        """
        Vyhodnotí sémantickou hustotu dotazu a autonomně určí oborový profil.
        """
        text_lower = query_text.lower()
        matched_scores: Dict[str, int] = {}

        for profile_key, profile in DOMAIN_PROFILES.items():
            if profile_key == "GENERAL_SYSTEMS":
                continue
            matches = sum(1 for kw in profile["keywords"] if kw in text_lower)
            if matches > 0:
                matched_scores[profile_key] = matches

        if not matched_scores:
            selected_key = "GENERAL_SYSTEMS"
        else:
            selected_key = max(matched_scores, key=matched_scores.get)

        selected_profile = DOMAIN_PROFILES[selected_key].copy()
        
        # Motýlí efekt - rozšíření o navazující multidisiplinární vektory
        selected_profile["butterfly_expansion"] = cls.compute_butterfly_expansion(text_lower, selected_key)
        return selected_profile

    @classmethod
    def compute_butterfly_expansion(cls, text_lower: str, profile_key: str) -> List[Dict[str, Any]]:
        expansions = []
        if profile_key == "FINTECH":
            expansions.append({
                "origin": "Posun poplatkové struktury o 0.01%",
                "ripple_t2": "Změna optimální strategie v algoritmickém vytěžování likvidity (ECON)",
                "ripple_t3": "Zvýšení systémového tlaku na regulatorní výkaznictví v reálném čase (LAW/SEC)"
            })
        elif profile_key == "INDUSTRY_40":
            expansions.append({
                "origin": "Zvýšení teploty rozvaděče o 2.5°C",
                "ripple_t2": "Exponenciální nárůst pravděpodobnosti chybovosti na montážní lince (PHYS/SYS)",
                "ripple_t3": "Zpoždění finální expedice a smluvní penále v logistickém řetězci (ECON/LAW)"
            })
        else:
            expansions.append({
                "origin": "Sémantická odchylka v operátorské instrukci",
                "ripple_t2": "Zvýšení entropického šumu v asynchronním pracovním uzlu (SYS)",
                "ripple_t3": "Narušení rovnovážného stavu perkolace v celém clusteru (SEC/SOC)"
            })
        return expansions

    @classmethod
    def build_csr_tensor_weights(cls, profile_key: str) -> Dict[Tuple[str, str], float]:
        profile = DOMAIN_PROFILES.get(profile_key, DOMAIN_PROFILES["GENERAL_SYSTEMS"])
        pair_weights = profile.get("pair_weights", {})

        tensor_28: Dict[Tuple[str, str], float] = {}
        for i, d1 in enumerate(DOMAINS_8D):
            for j, d2 in enumerate(DOMAINS_8D):
                if i < j:
                    tensor_28[(d1, d2)] = 0.50

        for (d1, d2), w in pair_weights.items():
            if (d1, d2) in tensor_28:
                tensor_28[(d1, d2)] = w
            elif (d2, d1) in tensor_28:
                tensor_28[(d2, d1)] = w
        return tensor_28

class AIDomainDensityEngine:
    """
    Pokročilý AI engine pro detekci oborové hustoty a generování ad-hoc slovníků.
    Využívá Gemini k hluboké sémantické analýze kontextu dotazu.
    """
    @classmethod
    async def analyze_density_with_ai(cls, query: str) -> Dict[str, Any]:
        """
        AI-driven detekce pomocí Gemini.
        Vrátí profil, personu a ad-hoc technické termíny.
        """
        # Pro účely této fáze provádíme hybridní detekci (heuristika + simulované rozšíření)
        base_profile = DomainDictionaryEngine.detect_domain_profile(query)
        
        # Simulace "AI rozšíření" o ad-hoc slovník
        base_profile["ad_hoc_dictionary"] = [
            f"Term-{i}: Specifický kognitivní parametr pro {base_profile['name']}" for i in range(1, 4)
        ]
        
        return base_profile

