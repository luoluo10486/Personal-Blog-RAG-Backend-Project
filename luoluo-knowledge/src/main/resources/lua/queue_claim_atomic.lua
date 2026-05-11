local queueKey = KEYS[1]
local requestId = ARGV[1]
local maxRank = tonumber(ARGV[2])

local rank = redis.call('ZRANK', queueKey, requestId)
if not rank then return {0} end
if rank >= maxRank then return {0} end

local score = redis.call('ZSCORE', queueKey, requestId)
redis.call('ZREM', queueKey, requestId)
return {1, score}
