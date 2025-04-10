-- Copyright 2025 Google LLC
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.

CREATE TABLE leaderboard (
    username VARCHAR(255) NOT NULL,
    score DOUBLE PRECISION NOT NULL,
    PRIMARY KEY (username)
);

-- Create an index to ensure efficient ordering by score (descending)
CREATE INDEX idx_leaderboard_score ON leaderboard (score DESC);

-- Create an index to ensure efficient ordering by score (ascending)
CREATE INDEX idx_leaderboard_score_asc ON leaderboard (score ASC);