package com.example.cineone_admin.dto.membership;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyMembershipResponse {
    private String fullName;
    private String email;
    private String currentTier;                 
    private BigDecimal totalSpent;              
    private String totalSpentDisplay;           
    
    // Tiến độ lên hạng tiếp theo
    private String nextTier;                    
    private BigDecimal nextTierMinSpending;     
    private String nextTierMinSpendingDisplay;  
    private BigDecimal spendingToNextTier;     
    private String spendingToNextTierDisplay;   
    private Double progressPercentage;          
    
  
    private List<String> currentTierBenefits;   

    private String membershipCycle;

    private List<TierDetailDto> allTiers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TierDetailDto {
        private Long tierId;
        private String name;                    
        private BigDecimal minSpending;         
        private String minSpendingDisplay;      
        private String description;
        private List<String> benefits;
        private boolean isCurrent;              
        private String colorHex;                
    }
}