import subprocess,sys,tempfile,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
SCRIPT=ROOT/'tools/requirements/validate-methodology-assurance.py'
BASE='''methodology_assurance: ENFORCED\nsystem_of_interest: Debina\nactor_goal: submit\ngoal_level: user_goal\nprimary_actor_type: human_role\ndetail_profile: ESSENTIAL\ncollaboration_status: NOT_REVIEWED\narchitecture_evaluation_type: ATAM_INSPIRED_DESK_REVIEW\nBF-1. actor acts\nAF-1A. At BF-1, alternate\n'''
class AssuranceAdversarialTests(unittest.TestCase):
 def check(self,text,ok):
  with tempfile.TemporaryDirectory() as d:
   p=Path(d); (p/'x.md').write_text(text); r=subprocess.run([sys.executable,str(SCRIPT),str(p)],cwd=ROOT,text=True,capture_output=True); self.assertEqual(0 if ok else 1,r.returncode,r.stdout)
 def test_internal_actor_fails(self): self.check(BASE+'primary_actor: payment-lifecycle\n',False)
 def test_unanchored_extension_fails(self): self.check(BASE.replace('At BF-1','At BF-9'),False)
 def test_false_collaboration_fails(self): self.check(BASE+'origin: AI_DRAFT\ncollaboration_status: STAKEHOLDER_CONFIRMED\nreview_evidence: []\n',False)
 def test_atam_overclaim_fails(self): self.check(BASE.replace('ATAM_INSPIRED_DESK_REVIEW','FULL_ATAM'),False)
 def test_component_name_and_technical_slice_fail(self): self.check(BASE+'name: pacs.002 processor\nslice_type: TECHNICAL_TASK\n',False)
 def test_material_question_ready_and_authority_inversion_fail(self): self.check(BASE+'material_questions_open: true\nreadiness: READY\nexternal_claim: true\nsource_authority: java\n',False)
 def test_valid_fixture_passes(self): self.check(BASE,True)
