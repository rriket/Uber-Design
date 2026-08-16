package com.uberclone.ratings.service;

import com.uberclone.ratings.domain.Rating;
import com.uberclone.ratings.dto.*;
import com.uberclone.ratings.repo.RatingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RatingsService {

    private final RatingRepository repo;

    @Transactional
    public RatingResponse submit(String ratedByUserId, SubmitRatingRequest req) {
        repo.findByRideIdAndRatedRole(req.rideId(), req.ratedRole())
                .ifPresent(r -> { throw new IllegalStateException("Rating already submitted for this ride/role"); });

        Rating r = Rating.builder()
                .rideId(req.rideId())
                .ratedByUserId(ratedByUserId)
                .ratedUserId(req.ratedUserId())
                .ratedRole(req.ratedRole())
                .stars(req.stars())
                .comment(req.comment())
                .build();
        r = repo.save(r);
        return toDto(r);
    }

    public List<RatingResponse> forRide(UUID rideId) {
        return repo.findByRideId(rideId).stream().map(this::toDto).toList();
    }

    public UserRatingSummary summary(String userId, String role) {
        Double avg = repo.avgStars(userId, role);
        long count = repo.countByRatedUserIdAndRatedRole(userId, role);
        return new UserRatingSummary(userId, role, avg == null ? 0.0 : Math.round(avg * 10.0) / 10.0, count);
    }

    public List<RatingResponse> historyFor(String userId, String role) {
        return repo.findByRatedUserIdAndRatedRoleOrderByCreatedAtDesc(userId, role)
                .stream().map(this::toDto).toList();
    }

    private RatingResponse toDto(Rating r) {
        return new RatingResponse(r.getId(), r.getRideId(), r.getRatedByUserId(),
                r.getRatedUserId(), r.getRatedRole(), r.getStars(), r.getComment(), r.getCreatedAt());
    }
}
