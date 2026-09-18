from pydantic import BaseModel, Field


class AnalyzeRequest(BaseModel):
    repository_path: str = Field(min_length=1)
    old_commit: str = Field(pattern=r"^[0-9a-fA-F]{40}$")
    new_commit: str = Field(pattern=r"^[0-9a-fA-F]{40}$")
